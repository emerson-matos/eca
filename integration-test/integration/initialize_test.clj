(ns integration.initialize-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [integration.eca :as eca]
   [integration.fixture :as fixture]
   [matcher-combinators.matchers :as m]
   [matcher-combinators.test :refer [match?]]))

(eca/clean-after-test)

(deftest default-initialize-and-shutdown
  (eca/start-process!)

  (testing "initialize request with default config"
    (is (match?
         {:models ["anthropic/claude-3-5-haiku-20241022"
                   "anthropic/claude-opus-4-1-20250805"
                   "anthropic/claude-opus-4-20250514"
                   "anthropic/claude-sonnet-4-20250514"
                   "github-copilot/claude-sonnet-4"
                   "github-copilot/gemini-2.5-pro"
                   "github-copilot/gpt-4.1"
                   "github-copilot/gpt-5"
                   "github-copilot/gpt-5-mini"
                   "openai/gpt-4.1"
                   "openai/gpt-5"
                   "openai/gpt-5-mini"
                   "openai/gpt-5-nano"
                   "openai/o3"
                   "openai/o4-mini"]
          :chatDefaultModel "anthropic/claude-sonnet-4-20250514"
          :chatBehaviors ["agent" "plan"]
          :chatDefaultBehavior "plan"
          :chatWelcomeMessage "Welcome to ECA!\n\nType '/' for commands\n\n"}
         (eca/request! (fixture/initialize-request
                        {:initializationOptions (merge fixture/default-init-options
                                                       {:chat {:defaultBehavior "plan"}})})))))

  (testing "initialized notification"
    (eca/notify! (fixture/initialized-notification)))

  (testing "Native tools updated"
    (is (match?
         {:type "native"
          :name "ECA"
          :status "running"
          :tools (m/pred seq)}
         (eca/client-awaits-server-notification :tool/serverUpdated))))

  (testing "shutdown request"
    (is (match?
         nil
         (eca/request! (fixture/shutdown-request)))))

  (testing "exit notification"
    (eca/notify! (fixture/exit-notification))))

(deftest initialize-with-custom-providers
  (eca/start-process!)
  (testing "initialize request with custom providers"
    (is (match?
         {:models ["anthropic/claude-3-5-haiku-20241022"
                   "anthropic/claude-opus-4-1-20250805"
                   "anthropic/claude-opus-4-20250514"
                   "anthropic/claude-sonnet-4-20250514"
                   "github-copilot/claude-sonnet-4"
                   "github-copilot/gemini-2.5-pro"
                   "github-copilot/gpt-4.1"
                   "github-copilot/gpt-5"
                   "github-copilot/gpt-5-mini"
                   "my-custom/bar2"
                   "my-custom/foo1"
                   "openai/gpt-4.1"
                   "openai/gpt-5"
                   "openai/gpt-5-mini"
                   "openai/gpt-5-nano"
                   "openai/o3"
                   "openai/o4-mini"]
          :chatDefaultModel "myCustom/bar-2"
          :chatBehaviors ["agent" "plan"]
          :chatDefaultBehavior "agent"
          :chatWelcomeMessage "Welcome to ECA!\n\nType '/' for commands\n\n"}
         (eca/request! (fixture/initialize-request
                        {:initializationOptions (merge fixture/default-init-options
                                                       {:defaultModel "myCustom/bar-2"
                                                        :providers
                                                        {"myCustom" {:api "openai"
                                                                     :urlEnv "MY_CUSTOM_API_URL"
                                                                     :keyEnv "MY_CUSTOM_API_KEY"
                                                                     :models {"foo1" {}
                                                                              "bar2" {}}}}})}))))))
