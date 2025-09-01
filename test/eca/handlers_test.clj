(ns eca.handlers-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [eca.config :as config]
   [eca.db :as db]
   [eca.handlers :as handlers]
   [matcher-combinators.test :refer [match?]]))

(deftest initialize-test
  (testing "initializationOptions config is merged properly with default init config"
    (let [db* (atom {})]
      (with-redefs [handlers/sync-models! (constantly nil)
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
