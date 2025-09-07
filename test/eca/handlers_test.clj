(ns eca.handlers-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [eca.config :as config]
   [eca.db :as db]
   [eca.handlers :as handlers]
   [eca.models :as models]
   [eca.features.tools :as f.tools]
   [eca.messenger :as messenger]
   [matcher-combinators.test :refer [match?]]))

(defrecord MockMessenger [calls-atom]
  messenger/IMessenger
  (config-updated [_this params]
    (swap! calls-atom conj [:config-updated params]))
  (tool-server-updated [_this params]
    (swap! calls-atom conj [:tool-server-updated params]))
  (chat-content-received [_this _data] nil)
  (showMessage [_this _msg] nil)
  (editor-diagnostics [_this _uri] nil))

(deftest initialize-test
  (testing "initializationOptions config is merged properly with default init config"
    (let [db* (atom {})]
      (with-redefs [models/sync-models! (constantly nil)
                    db/load-db-from-cache! (constantly nil)]
        (is (match?
             {}
             (handlers/initialize {:db* db*} {:initialization-options
                                              {:pureConfig true
                                               :providers {"github-copilot" {:key "123"
                                                                             :models {"gpt-5" {:a 1}}}}}})))
        (is (match?
             {:providers {"github-copilot" {:key "123"
                                            :models {"gpt-5" {:a 1}
                                                     "gpt-5-mini" {}}
                                            :url string?}}}
             (config/all @db*)))))))

(deftest chat-selected-behavior-changed-test
  (testing "Switching to behavior with defaultModel updates model"
    (let [db* (atom {:last-config-notified {} :mcp-clients {}})
          messenger-calls (atom [])
          config {:behavior {"custom" {:defaultModel "gpt-4.1"}}}
          mock-messenger (->MockMessenger messenger-calls)]
      (handlers/chat-selected-behavior-changed {:db* db*
                                                :messenger mock-messenger
                                                :config config}
                                               {:behavior "custom"})
      ;; Should notify about model change and tool updates
      (let [config-calls (filter #(= (first %) :config-updated) @messenger-calls)
            tool-calls (filter #(= (first %) :tool-server-updated) @messenger-calls)]
        (is (match? [[:config-updated {:chat {:select-model "gpt-4.1"}}]] config-calls))
        (is (pos? (count tool-calls))))))

  (testing "Switching to behavior without defaultModel uses global default"
    (let [db* (atom {:last-config-notified {} :mcp-clients {}})
          messenger-calls (atom [])
          config {:defaultModel "claude-opus-4"
                  :behavior {"plan" {}}}
          mock-messenger (->MockMessenger messenger-calls)]
      (handlers/chat-selected-behavior-changed {:db* db*
                                                :messenger mock-messenger
                                                :config config}
                                               {:behavior "plan"})
      ;; Should notify about global default model and tool updates
      (let [config-calls (filter #(= (first %) :config-updated) @messenger-calls)
            tool-calls (filter #(= (first %) :tool-server-updated) @messenger-calls)]
        (is (match? [[:config-updated {:chat {:select-model "claude-opus-4"}}]] config-calls))
        (is (pos? (count tool-calls))))))

  (testing "Switching behavior updates tool status"
    (let [db* (atom {:mcp-clients {}})
          messenger-calls (atom [])
          config {:behavior {"plan" {:disabledTools ["eca_edit_file" "eca_write_file"]}}}
          mock-messenger (->MockMessenger messenger-calls)]
      (with-redefs [f.tools/native-tools (constantly [{:name "eca_edit_file"}
                                                       {:name "eca_read_file"}])]
        (handlers/chat-selected-behavior-changed {:db* db*
                                                  :messenger mock-messenger
                                                  :config config}
                                                 {:behavior "plan"})
        ;; Should call tool-server-updated with disabled status
        (let [tool-calls (filter #(= (first %) :tool-server-updated) @messenger-calls)]
          (is (pos? (count tool-calls)))
          ;; Check that eca_edit_file tool has disabled status applied
          (let [native-tool-call (first tool-calls)
                tools (get-in native-tool-call [1 :tools])
                edit-tool (first (filter #(= "eca_edit_file" (:name %)) tools))]
            (is (some? edit-tool))
            (is (true? (:disabled edit-tool))))))))

  (testing "Switching to undefined behavior uses defaults"
    (let [db* (atom {:last-config-notified {} :mcp-clients {}})
          messenger-calls (atom [])
          config {:defaultModel "fallback-model"}
          mock-messenger (->MockMessenger messenger-calls)]
      (handlers/chat-selected-behavior-changed {:db* db*
                                                :messenger mock-messenger
                                                :config config}
                                               {:behavior "nonexistent"})
      ;; Should notify about fallback model and tool updates
      (let [config-calls (filter #(= (first %) :config-updated) @messenger-calls)
            tool-calls (filter #(= (first %) :tool-server-updated) @messenger-calls)]
        (is (match? [[:config-updated {:chat {:select-model "fallback-model"}}]] config-calls))
        (is (pos? (count tool-calls)))))))
