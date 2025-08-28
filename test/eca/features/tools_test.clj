(ns eca.features.tools-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [eca.features.tools :as f.tools]
   [eca.features.tools.filesystem :as f.tools.filesystem]
   [eca.test-helper :as h]
   [matcher-combinators.matchers :as m]
   [matcher-combinators.test :refer [match?]]))

(deftest all-tools-test
  (testing "Include mcp tools"
    (is (match?
         (m/embeds [{:name "eval"
                     :description "eval code"
                     :parameters {"type" "object"
                                  :properties {"code" {:type "string"}}}
                     :origin :mcp}])
         (f.tools/all-tools "agent"
                            {:mcp-clients {:clojureMCP
                                           {:tools [{:name "eval"
                                                     :description "eval code"
                                                     :parameters {"type" "object"
                                                                  :properties {"code" {:type "string"}}}}]}}}
                            {}))))
  (testing "Include enabled native tools"
    (is (match?
         (m/embeds [{:name "eca_directory_tree"
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
