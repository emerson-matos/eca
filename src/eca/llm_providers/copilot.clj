(ns eca.llm-providers.copilot
  (:require
   [cheshire.core :as json]
   [eca.config :as config]
   [eca.features.login :as f.login]
   [hato.client :as http]))

(def ^:private client-id "Iv1.b507a08c87ecfe98")

(defn ^:private auth-headers []
  {"Content-Type" "application/json"
   "Accept" "application/json"
   "editor-plugin-version" "eca/*"
   "editor-version" (str "eca/" (config/eca-version))})

(defn ^:private oauth-url []
  (let [{:keys [body]} (http/post
                        "https://github.com/login/device/code"
                        {:headers (auth-headers)
                         :body (json/generate-string {:client_id client-id
                                                      :scope "read:user"})
                         :as :json})]
    {:user-code (:user_code body)
     :device-code (:device_code body)
     :url (:verification_uri body)}))

(defn ^:private oauth-access-token [device-code]
  (let [{:keys [status body]} (http/post
                               "https://github.com/login/oauth/access_token"
                               {:headers (auth-headers)
                                :body (json/generate-string {:client_id client-id
                                                             :device_code device-code
                                                             :grant_type "urn:ietf:params:oauth:grant-type:device_code"})
                                :throw-exceptions? false
                                :as :json})]
    (if (= 200 status)
      (:access_token body)
      (throw (ex-info (format "Github auth failed: %s" (pr-str body))
                      {:status status
                       :body body})))))

(defn ^:private oauth-renew-token [access-token]
  (let [{:keys [status body]} (http/get
                               "https://api.github.com/copilot_internal/v2/token"
                               {:headers (merge (auth-headers)
                                                {"authorization" (str "token " access-token)})
                                :throw-exceptions? false
                                :as :json})]
    (if-let [token (:token body)]
      {:api-key token
       :expires-at (:expires_at body)}
      (throw (ex-info (format "Error on copilot login: %s" body)
                      {:status status
                       :body body})))))

(defmethod f.login/login-step ["github-copilot" :login/start] [{:keys [db* chat-id provider]}]
  (let [{:keys [user-code device-code url]} (oauth-url)]
    (swap! db* assoc-in [:chats chat-id :login-provider] provider)
    (swap! db* assoc-in [:auth provider] {:step :login/waiting-user-confirmation
                                          :device-code device-code})
    {:message (format "Open your browser at `%s` and authenticate using the code: `%s`\nThen type anything in the chat and send it to continue the authentication."
                      url
                      user-code)}))

(defmethod f.login/login-step ["github-copilot" :login/waiting-user-confirmation] [{:keys [db* chat-id provider send-msg!]}]
  (let [access-token (oauth-access-token (get-in @db* [:auth provider :device-code]))
        {:keys [api-key expires-at]} (oauth-renew-token access-token)]
    (swap! db* update-in [:auth provider] merge {:step :login/done
                                                 :access-token access-token
                                                 :api-key api-key
                                                 :expires-at expires-at})
    (swap! db* update-in [:chats chat-id :status] :idle)
    (send-msg! "Login successful! You can now use the 'github-copilot' models.")))

(defmethod f.login/login-step ["github-copilot" :login/renew-token] [{:keys [db* provider]}]
  (let [access-token (get-in @db* [:auth provider :access-token])
        {:keys [api-key expires-at]} (oauth-renew-token access-token)]
    (swap! db* update-in [:auth provider] merge {:api-key api-key
                                                 :expires-at expires-at})))
