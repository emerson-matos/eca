(ns eca.config-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [eca.config :as config]
   [eca.test-helper :as h]
   [matcher-combinators.test :refer [match?]]))

(h/reset-components-before-test)

(deftest all-test
  (testing "Default config"
    (reset! config/initialization-config* {:pureConfig true})
    (is (match?
         {:pureConfig true
          :providers {"github-copilot" {:key nil
                                        :models {"gpt-5" {}}}}}
         (config/all {}))))
  (testing "deep merging initializationOptions with initial config"
    (reset! config/initialization-config* {:pureConfig true
                                           :providers {"githubCopilot" {:key "123"}}})
    (is (match?
         {:pureConfig true
          :providers {"github-copilot" {:key "123"
                                        :models {"gpt-5" {}}}}}
         (config/all {}))))
  (testing "providers and models are updated correctly"
    (reset! config/initialization-config* {:pureConfig true
                                           :providers {"customProvider" {:key "123"
                                                                         :models {:gpt-5 {}}}
                                                       "openrouter" {:models {"openai/o4-mini" {}}}}})
    (is (match?
         {:pureConfig true
          :providers {"custom-provider" {:key "123"
                                         :models {"gpt-5" {}}}
                      "openrouter" {:models {"openai/o4-mini" {}}}}}
         (config/all {})))))

(deftest deep-merge-test
  (testing "basic merge"
    (is (match?
         {:a 1
          :b 4
          :c 3
          :d 1}
         (#'config/deep-merge {:a 1}
                              {:b 2}
                              {:c 3}
                              {:b 4 :d 1}))))
  (testing "deep merging"
    (is (match?
         {:a 1
          :b {:c {:d 3
                  :e 4}}}
         (#'config/deep-merge {:a 1
                               :b {:c {:d 3}}}
                              {:b {:c {:e 4}}}))))
  (testing "deep merging maps with other keys"
    (is (match?
         {:a 1
          :b {:c {:e 3
                  :f 4}
              :d 2}}
         (#'config/deep-merge {:a 1
                               :b {:c {:e 3}
                                   :d 2}}
                              {:b {:c {:f 4}}})))
    (is (match?
         {:pureConfig true
          :providers {"github-copilot" {:models {"gpt-5" {}}
                                        :key "123"}}}
         (#'config/deep-merge {:providers {"github-copilot" {:models {"gpt-5" {}}}}}
                              {:pureConfig true
                               :providers {"github-copilot" {:key "123"}}})))))

(deftest normalize-fields-test
  (testing "stringfy only passed rules"
    (is (match?
         {:pureConfig true
          :providers {"custom-provider" {:key "123"
                                         :models {"gpt-5" {}}}
                      "openrouter" {:models {"openai/o4-mini" {}}}}}
         (#'config/normalize-fields
          {:stringfy
           [[:providers]
            [:providers :ANY :models]]}
          {"pureConfig" true
           "providers" {"custom-provider" {"key" "123"
                                           "models" {"gpt-5" {}}}
                        "openrouter" {"models" {"openai/o4-mini" {}}}}}))))
  (testing "kebab-case only passed rules"
    (is (match?
         {:pureConfig true
          :providers {"custom-provider" {:key "123"
                                         :models {"gpt-5" {}}}
                      "open-router" {:models {"openAi/o4-mini" {}}}}}
         (#'config/normalize-fields
          {:stringfy
           [[:providers]
            [:providers :ANY :models]]
           :kebab-case
           [[:providers]]}
          {"pureConfig" true
           "providers" {"customProvider" {"key" "123"
                                          "models" {"gpt-5" {}}}
                        "open-router" {"models" {"openAi/o4-mini" {}}}}})))))

(deftest diff-keeping-vectors-test
  (testing "like clojure.data/diff"
    (is (= {:b 3}
           (#'config/diff-keeping-vectors {:a 1
                                           :b 2}
                                          {:a 1
                                           :b 3})))
    (is (= nil
           (#'config/diff-keeping-vectors {:a {:b 2}
                                           :c 3}
                                          {:a {:b 2}
                                           :c 3})))
    (is (= {:a {:b 3}}
           (#'config/diff-keeping-vectors {:a {:b 2}
                                           :c 3}
                                          {:a {:b 3}
                                           :c 3}))))
  (testing "if a vector value changed, we keep vector from b"
    (is (= {:b [:bar :foo]}
           (#'config/diff-keeping-vectors {:a 1
                                           :c 3
                                           :b [:bar]}
                                          {:c 3
                                           :b [:bar :foo]})))
    (is (= {:b [:bar]}
           (#'config/diff-keeping-vectors {:a 1
                                           :c 3
                                           :b [:bar :foo]}
                                          {:c 3
                                           :b [:bar]})))
    (is (= {:b [:bar]}
           (#'config/diff-keeping-vectors {:a 1
                                           :c 3
                                           :b []}
                                          {:c 3
                                           :b [:bar]})))
    (is (= {:b []}
           (#'config/diff-keeping-vectors {:a 1
                                           :c 3
                                           :b [:bar]}
                                          {:c 3
                                           :b []})))))
