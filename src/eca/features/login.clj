(ns eca.features.login
  (:require
   [clojure.string :as string]
   [eca.db :as db]
   [eca.messenger :as messenger]
   [eca.models :as models]))

(defmulti login-step (fn [ctx] [(:provider ctx) (:step ctx)]))

(defmethod login-step :default [_] {:error "Unkown provider-id"})

(defn start [chat-id provider db*]
  (login-step {:chat-id chat-id
               :step :login/start
               :provider provider
               :db* db*}))

(defn continue [{:keys [message chat-id request-id]} db* messenger config]
  (let [provider (get-in @db* [:chats chat-id :login-provider])
        step (get-in @db* [:auth provider :step])
        input (string/trim message)
        ctx {:chat-id chat-id
             :step step
             :input input
             :db* db*
             :config config
             :messenger messenger
             :provider provider
             :send-msg! (fn [msg]
                          (messenger/chat-content-received
                           messenger
                           {:chat-id chat-id
                            :request-id request-id
                            :role "system"
                            :content {:type :text
                                      :text msg}})
                          (messenger/chat-content-received
                           messenger
                           {:chat-id chat-id
                            :request-id request-id
                            :role "system"
                            :content {:type :progress
                                      :state :finished}}))}]
    (messenger/chat-content-received
     messenger
     {:chat-id chat-id
      :request-id request-id
      :role "user"
      :content {:type :text
                :text (str input "\n")}})
    (login-step ctx)
    (db/update-global-cache! @db*)
    {:chat-id chat-id
     :status step}))

(defn renew-auth! [provider db*]
  (login-step
   {:provider provider
    :step :login/renew-token
    :db* db*})
  (db/update-global-cache! @db*))

(defn login-done! [{:keys [db* config messenger]}]
  (models/sync-models! db*
                       config
                       (fn [new-models]
                         (messenger/config-updated
                          messenger
                          {:chat
                           {:models (sort (keys new-models))}}))))
