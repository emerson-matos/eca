(ns eca.features.tools.shell-test
  (:require
   [babashka.fs :as fs]
   [babashka.process :as p]
   [clojure.test :refer [deftest is testing]]
   [eca.features.tools.shell :as f.tools.shell]
   [eca.test-helper :as h]
   [matcher-combinators.test :refer [match?]]))

(deftest shell-command-test
  (testing "inexistent working_directory"
    (is (match?
         {:error true
          :contents [{:type :text
                      :text (format "working directory %s does not exist" (h/file-path "/baz"))}]}
         (with-redefs [fs/exists? (constantly false)]
           ((get-in f.tools.shell/definitions ["eca_shell_command" :handler])
            {"command" "ls -lh"
             "working_directory" (h/file-path "/baz")}
            {:db {:workspace-folders [{:uri (h/file-uri "file:///project/foo") :name "foo"}]}})))))
  (testing "command exited with non-zero exit code"
    (is (match?
         {:error true
          :contents [{:type :text
                      :text "Exit code 1"}
                     {:type :text
                      :text "Stderr:\nSome error"}]}
         (with-redefs [fs/exists? (constantly true)
                       p/shell (constantly {:exit 1 :err "Some error"})]
           ((get-in f.tools.shell/definitions ["eca_shell_command" :handler])
            {"command" "ls -lh"}
            {:db {:workspace-folders [{:uri (h/file-uri "file:///project/foo") :name "foo"}]}})))))
  (testing "command succeeds"
    (is (match?
         {:error false
          :contents [{:type :text
                      :text "Some text"}]}
         (with-redefs [fs/exists? (constantly true)
                       p/shell (constantly {:exit 0 :out "Some text" :err "Other text"})]
           ((get-in f.tools.shell/definitions ["eca_shell_command" :handler])
            {"command" "ls -lh"}
            {:db {:workspace-folders [{:uri (h/file-uri "file:///project/foo") :name "foo"}]}})))))
  (testing "command succeeds with different working directory"
    (is (match?
         {:error false
          :contents [{:type :text
                      :text "Some text"}]}
         (with-redefs [fs/exists? (constantly true)
                       p/shell (constantly {:exit 0 :out "Some text" :err "Other text"})]
           ((get-in f.tools.shell/definitions ["eca_shell_command" :handler])
            {"command" "ls -lh"
             "working_directory" (h/file-path "/project/foo/src")}
            {:db {:workspace-folders [{:uri (h/file-uri "file:///project/foo") :name "foo"}]}})))))
  (testing "command does not fails if not in excluded config"
    (is (match?
         {:error false
          :contents [{:type :text
                      :text "Some text"}]}
         (with-redefs [fs/exists? (constantly true)
                       p/shell (constantly {:exit 0 :out "Some text" :err "Other text"})]
           ((get-in f.tools.shell/definitions ["eca_shell_command" :handler])
            {"command" "rm -r /project/foo/src"}
            {:db {:workspace-folders [{:uri (h/file-uri "file:///project/foo") :name "foo"}]}
             :config {:nativeTools {:shell {:enabled true
                                            :excludeCommands ["ls" "cd"]}}}})))))
  (testing "command fails if in excluded config"
    (is (match?
         {:error true
          :contents [{:type :text
                      :text "Cannot run command 'rm' because it is excluded by eca config."}]}
         (with-redefs [fs/exists? (constantly true)]
           ((get-in f.tools.shell/definitions ["eca_shell_command" :handler])
            {"command" "rm -r /project/foo/src"}
            {:db {:workspace-folders [{:uri (h/file-uri "file:///project/foo") :name "foo"}]}
             :config {:nativeTools {:shell {:enabled true
                                            :excludeCommands ["ls" "rm"]}}}}))))))

(deftest shell-require-approval-fn-test
  (let [approval-fn (get-in f.tools.shell/definitions ["eca_shell_command" :require-approval-fn])
        db {:workspace-folders [{:uri (h/file-uri "file:///project/foo") :name "foo"}]}]
    (testing "returns nil when working_directory is not provided"
      (is (nil? (approval-fn nil {:db db})))
      (is (nil? (approval-fn {} {:db db}))))
    (testing "returns nil when working_directory does not exist"
      (with-redefs [fs/exists? (constantly false)]
        (is (nil? (approval-fn {"working_directory" (h/file-path "/project/foo/src")} {:db db})))))
    (testing "returns false when working_directory equals a workspace root"
      (with-redefs [fs/exists? (constantly true)
                    fs/canonicalize identity]
        (is (false? (approval-fn {"working_directory" (h/file-path "/project/foo")} {:db db})))))
    (testing "returns false when working_directory is a subdirectory of a workspace root"
      (with-redefs [fs/exists? (constantly true)
                    fs/canonicalize identity]
        (is (false? (approval-fn {"working_directory" (h/file-path "/project/foo/src")} {:db db})))))
    (testing "returns true when working_directory is outside any workspace root"
      (with-redefs [fs/exists? (constantly true)
                    fs/canonicalize identity]
        (is (true? (approval-fn {"working_directory" (h/file-path "/other/place")} {:db db})))))))
