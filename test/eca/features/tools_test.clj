(ns eca.features.tools-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [eca.features.tools :as f.tools]
   [eca.features.tools.filesystem :as f.tools.filesystem]
   [eca.test-helper :as h]
   [matcher-combinators.matchers :as m]
   [matcher-combinators.test :refer [match?]]
   [eca.config :as config]
   [babashka.process :as p]))

(deftest all-tools-test
  (testing "Include mcp tools"
    (is (match?
         (m/embeds [{:name "eval"
                     :server "clojureMCP"
                     :description "eval code"
                     :parameters {"type" "object"
                                  :properties {"code" {:type "string"}}}
                     :origin :mcp}])
         (f.tools/all-tools "agent"
                            {:mcp-clients {"clojureMCP"
                                           {:tools [{:name "eval"
                                                     :description "eval code"
                                                     :parameters {"type" "object"
                                                                  :properties {"code" {:type "string"}}}}]}}}
                            {}))))
  (testing "Include enabled native tools"
    (is (match?
         (m/embeds [{:name "eca_directory_tree"
                     :server "eca"
                     :description string?
                     :parameters some?
                     :origin :native}])
         (f.tools/all-tools "agent" {} {:nativeTools {:filesystem {:enabled true}}}))))
  (testing "Do not include disabled native tools"
    (is (match?
         (m/embeds [(m/mismatch {:name "eca_directory_tree"})])
         (f.tools/all-tools "agent" {} {:nativeTools {:filesystem {:enabled false}}}))))
  (testing "Plan mode includes preview tool but excludes mutating tools"
    (let [plan-tools (f.tools/all-tools "plan" {} {:nativeTools {:filesystem {:enabled true}
                                                                 :shell {:enabled true}}})
          tool-names (set (map :name plan-tools))]
      ;; Verify that preview tool is included
      (is (contains? tool-names "eca_preview_file_change"))
      ;; Verify that shell command is now allowed in plan mode (with restrictions in prompt)
      (is (contains? tool-names "eca_shell_command"))
      ;; Verify that mutating tools are excluded
      (is (not (contains? tool-names "eca_edit_file")))
      (is (not (contains? tool-names "eca_write_file")))
      (is (not (contains? tool-names "eca_move_file")))))
  (testing "Do not include plan edit tool if agent behavior"
    (is (match?
         (m/embeds [(m/mismatch {:name "eca_preview_file_change"})
                    {:name "eca_edit_file"}])
         (f.tools/all-tools "agent" {} {:nativeTools {:filesystem {:enabled true}}}))))
  (testing "Replace special vars description"
    (is (match?
         (m/embeds [{:name "eca_directory_tree"
                     :description (format "Only in %s" (h/file-path "/path/to/project/foo"))
                     :parameters some?
                     :origin :native}])
         (with-redefs [f.tools.filesystem/definitions {"eca_directory_tree" {:description "Only in $workspaceRoots"
                                                                             :parameters {}}}]
           (f.tools/all-tools "agent" {:workspace-folders [{:name "foo" :uri (h/file-uri "file:///path/to/project/foo")}]}
                              {:nativeTools {:filesystem {:enabled true}}}))))))

(deftest manual-approval?-test
  (let [all-tools [{:name "eca_read" :server "eca"}
                   {:name "eca_write" :server "eca"}
                   {:name "eca_shell" :server "eca" :require-approval-fn (constantly true)}
                   {:name "eca_plan" :server "eca" :require-approval-fn (constantly false)}
                   {:name "request" :server "web"}
                   {:name "download" :server "web"}]]
    (testing "tool has require-approval-fn which returns true"
      (is (= :ask (f.tools/approval all-tools "eca_shell" {} {} {}))))
    (testing "tool has require-approval-fn which returns false we ignore it"
      (is (= :ask (f.tools/approval all-tools "eca_plan" {} {} {}))))
    (testing "if legacy-manual-approval present, considers it"
      (is (= :ask (f.tools/approval all-tools "request" {} {} {:toolCall {:manualApproval true}}))))
    (testing "if approval config is provided"
      (testing "when matches allow config"
        (is (= :allow (f.tools/approval all-tools "request" {} {} {:toolCall {:approval {:allow {"web__request" {}}}}})))
        (is (= :allow (f.tools/approval all-tools "eca_read" {} {} {:toolCall {:approval {:allow {"eca_read" {}}}}})))
        (is (= :allow (f.tools/approval all-tools "request" {} {} {:toolCall {:approval {:allow {"web" {}}}}}))))
      (testing "when matches ask config"
        (is (= :ask (f.tools/approval all-tools "request" {} {} {:toolCall {:approval {:ask {"web__request" {}}}}})))
        (is (= :ask (f.tools/approval all-tools "eca_read" {} {} {:toolCall {:approval {:ask {"eca_read" {}}}}})))
        (is (= :ask (f.tools/approval all-tools "request" {} {} {:toolCall {:approval {:ask {"web" {}}}}}))))
      (testing "when matches deny config"
        (is (= :deny (f.tools/approval all-tools "request" {} {} {:toolCall {:approval {:deny {"web__request" {}}}}})))
        (is (= :deny (f.tools/approval all-tools "eca_read" {} {} {:toolCall {:approval {:deny {"eca_read" {}}}}})))
        (is (= :deny (f.tools/approval all-tools "request" {} {} {:toolCall {:approval {:deny {"web" {}}}}}))))
      (testing "when contains argsMatchers"
        (testing "has arg but not matches"
          (is (= :ask (f.tools/approval all-tools "request" {"url" "http://bla.com"} {}
                                        {:toolCall {:approval {:allow {"web__request" {:argsMatchers {"url" [".*foo.*"]}}}}}}))))
        (testing "has arg and matches for allow"
          (is (= :allow (f.tools/approval all-tools "request" {"url" "http://foo.com"} {}
                                          {:toolCall {:approval {:allow {"web__request" {:argsMatchers {"url" [".*foo.*"]}}}}}})))
          (is (= :allow (f.tools/approval all-tools "request" {"url" "foobar"} {}
                                          {:toolCall {:approval {:allow {"web__request" {:argsMatchers {"url" ["foo.*"]}}}}}}))))
        (testing "has arg and matches for deny"
          (is (= :deny (f.tools/approval all-tools "request" {"url" "http://foo.com"} {}
                                         {:toolCall {:approval {:deny {"web__request" {:argsMatchers {"url" [".*foo.*"]}}}}}})))
          (is (= :deny (f.tools/approval all-tools "request" {"url" "foobar"} {}
                                         {:toolCall {:approval {:deny {"web__request" {:argsMatchers {"url" ["foo.*"]}}}}}}))))
        (testing "has not that arg"
          (is (= :ask (f.tools/approval all-tools "request" {"crazy-url" "http://foo.com"} {}
                                        {:toolCall {:approval {:allow {"web__request" {:argsMatchers {"url" [".*foo.*"]}}}}}}))))))
    (testing "if no approval config matches"
      (testing "checks byDefault"
        (testing "when 'ask', return true"
          (is (= :ask (f.tools/approval all-tools "request" {} {} {:toolCall {:approval {:byDefault "ask"}}}))))
        (testing "when 'allow', return false"
          (is (= :allow (f.tools/approval all-tools "request" {} {} {:toolCall {:approval {:byDefault "allow"}}})))))
      (testing "fallback to manual approval"
        (is (= :ask (f.tools/approval all-tools "request" {} {} {})))))))

(deftest custom-tools-test
  (testing "when a valid tool is configured"
    (let [mock-custom-tools {"file-search"
                             {:description "Finds files."
                              :command     ["find" "{{directory}}" "-name" "{{pattern}}"]
                              :schema      {:properties {:directory {:type "string"}
                                                         :pattern   {:type "string"}}
                                            :required    ["directory" "pattern"]}}}]
      (testing "and the command executes successfully"
        (with-redefs [p/sh (fn [command-vec & _]
                             (is (= ["find" "/tmp" "-name" "*.clj"] command-vec))
                             {:out "mocked-output" :exit 0})]
          (let [config {:custom-tools mock-custom-tools}
                native-defs (#'f.tools/native-definitions {} config)
                custom-tool-def (get native-defs "file-search")]
            (is (some? custom-tool-def) "The custom tool should be loaded.")
            (let [result ((:handler custom-tool-def) {:directory "/tmp" :pattern "*.clj"} {})]
              (is (= "mocked-output" result) "The tool should return the mocked shell output."))))))

    (testing "when multiple tools are configured"
      (let [mock-custom-tools {"git-status"
                               {:description "Gets git status"
                                :command ["git" "status"]}
                               "echo-message"
                               {:description "Echoes a message"
                                :command ["echo" "{{message}}"]
                                :schema {:properties {:message {:type "string"}} :required ["message"]}}}]
        (with-redefs [p/sh (fn [command-vec & _]
                             (condp = command-vec
                               ["git" "status"] {:out "On branch main" :exit 0}
                               ["echo" "Hello World"] {:out "Hello World" :exit 0}
                               (is false "Unexpected command received by mock p/sh")))]
          (let [config {:custom-tools mock-custom-tools}
                native-defs (#'f.tools/native-definitions {} config)
                git-status-handler (get-in native-defs ["git-status" :handler])
                echo-handler (get-in native-defs ["echo-message" :handler])]
            (is (some? git-status-handler) "Git status tool should be loaded.")
            (is (some? echo-handler) "Echo message tool should be loaded.")
            (is (= "On branch main" (git-status-handler {} {})))
            (is (= "Hello World" (echo-handler {:message "Hello World"} {})))))))

    (testing "when the custom tools config is empty or missing"
      (testing "with an empty map"
        (let [config {:custom-tools {}}
              native-defs (#'f.tools/native-definitions {} config)]
          (is (not (contains? native-defs "file-search")) "No custom tools should be loaded.")))
      (testing "with the key missing from the config"
        (let [config {}
              native-defs (#'f.tools/native-definitions {} config)]
          (is (not (contains? native-defs "file-search")) "No custom tools should be loaded."))))))
