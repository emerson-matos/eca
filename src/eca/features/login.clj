(ns eca.features.login
  (:require
   [clojure.string :as string]
   [eca.db :as db]
   [eca.llm-providers.anthropic :as llm-providers.anthropic]
   [eca.llm-providers.copilot :as llm-providers.copilot]
   [eca.messenger :as messenger]))

(defn start-login [chat-id provider db*]
  (case provider
    "anthropic"
    (let [{:keys [verifier url]} (llm-providers.anthropic/oauth-url :console)]
      (swap! db* assoc-in [:chats chat-id :login-provider] provider)
      (swap! db* assoc-in [:auth provider] {:step :login/waiting-provider-code
                                            :verifier verifier})
      {:message (format "Open your browser at `%s` and authenticate at Anthropic.\nThen paste the code generated in the chat and send it to continue the authentication."
                        url)})
    "github-copilot"
    (let [{:keys [user-code device-code url]} (llm-providers.copilot/oauth-url)]
      (swap! db* assoc-in [:chats chat-id :login-provider] provider)
      (swap! db* assoc-in [:auth provider] {:step :login/waiting-user-confirmation
                                            :device-code device-code})
      {:message (format "Open your browser at `%s` and authenticate using the code: `%s`\nThen type anything in the chat and send it to continue the authentication."
                        url
                        user-code)})
    {:message "Unknown provider-id"}))

(defn continue [{:keys [message chat-id request-id]} db* messenger]
  (let [provider (get-in @db* [:chats chat-id :login-provider])
        step (get-in @db* [:auth provider :step])]
    (case step
      :login/waiting-provider-code
      (case provider
        "anthropic" (let [provider-code (string/trim message)
                          {:keys [access-token refresh-token expires-at]} (llm-providers.anthropic/oauth-credentials provider-code (get-in @db* [:auth provider :verifier]))
                          api-key (llm-providers.anthropic/create-api-key access-token)]
                      (swap! db* update-in [:auth provider] merge {:step :login/done
                                                                   :access-token access-token
                                                                   :refresh-token refresh-token
                                                                   :api-token api-key
                                                                   :expires-at expires-at})
                      (swap! db* update-in [:chats chat-id :status] :idle)
                      (messenger/chat-content-received
                       messenger
                       {:chat-id chat-id
                        :request-id request-id
                        :role "system"
                        :content {:type :text
                                  :text "Login successful! You can now use the 'anthropic' models."}})
                      (messenger/chat-content-received
                       messenger
                       {:chat-id chat-id
                        :request-id request-id
                        :role "system"
                        :content {:type :progress
                                  :state :finished}})))
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
