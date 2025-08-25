(ns eca.features.login
  (:require
   [eca.db :as db]
   [eca.llm-providers.copilot :as llm-providers.copilot]
   [eca.messenger :as messenger]))

(defn start-login [chat-id provider db*]
  (case provider
    "github-copilot"
    (let [{:keys [user-code device-code url]} (llm-providers.copilot/oauth-url)]
      (swap! db* assoc-in [:chats chat-id :login-provider] provider)
      (swap! db* assoc-in [:auth provider] {:step :login/waiting-user-confirmation
                                            :device-code device-code})
      {:message (format "Open your browser at `%s` and authenticate using the code: `%s`\nThen type anything in the chat and send it to continue the authentication."
                        url
                        user-code)})))

(defn continue [{:keys [chat-id request-id]} db* messenger]
  (let [provider (get-in @db* [:chats chat-id :login-provider])
        step (get-in @db* [:auth provider :step])]
    (case step
      :login/waiting-user-confirmation
      (case provider
        "github-copilot" (let [access-token (llm-providers.copilot/oauth-access-token (get-in @db* [:auth provider :device-code]))
                               {:keys [api-token expires-at]} (llm-providers.copilot/oauth-renew-token access-token)]
                           (swap! db* update-in [:auth provider] merge {:step :login/done
                                                                        :access-token access-token
                                                                        :api-token api-token
                                                                        :expires-at expires-at})
                           (swap! db* update-in [:chats chat-id :status] :idle)
                           (messenger/chat-content-received
                            messenger
                            {:chat-id chat-id
                             :request-id request-id
                             :role "system"
                             :content {:type :text
                                       :text "Login successful! You can now use the 'github-copilot' models."}})
                           (messenger/chat-content-received
                            messenger
                            {:chat-id chat-id
                             :request-id request-id
                             :role "system"
                             :content {:type :progress
                                       :state :finished}}))))
    (db/update-global-cache! @db*)))

(defn renew-auth! [provider db*]
  (case provider
    "github-copilot"
    (let [access-token (get-in @db* [:auth provider :access-token])
          {:keys [api-token expires-at]} (llm-providers.copilot/oauth-renew-token access-token)]
      (swap! db* update-in [:auth provider] merge {:api-token api-token
                                                   :expires-at expires-at}))))
