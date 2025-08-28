(ns eca.features.tools.shell
  (:require [babashka.fs :as fs]
            [babashka.process :as p]
            [clojure.string :as string]
            [eca.config :as config]
            [eca.features.tools.util :as tools.util]
            [eca.logger :as logger]
            [eca.shared :as shared]))

(set! *warn-on-reflection* true)

(def ^:private logger-tag "[TOOLS-SHELL]")

;; Plan mode command restrictions
;; TODO - We might see that this is not needed and prompt handles it.
;;        If we don't get 'Command bocked in plan model' error for some time, let's remove it.
;;        Maybe we should use allowed patterns only, especially for user's custom behaviors.
(def ^:private plan-safe-commands-default
  #{"git" "ls" "find" "grep" "rg" "ag" "cat" "head" "tail"
    "pwd" "which" "file" "stat" "tree" "date" "whoami"
    "env" "echo" "wc" "du" "df"})

(def ^:private plan-forbidden-patterns-default
  [#">"                              ; output redirection
   #"\|\s*(tee|dd|xargs)"            ; dangerous pipes
   #"\b(sed|awk|perl)\s+.*-i"       ; in-place editing
   #"\b(rm|mv|cp|touch|mkdir)\b"    ; file operations
   #"git\s+(add|commit|push)"       ; git mutations
   #"npm\s+install"                 ; package installs
   #"-c\s+[\"'].*open.*[\"']w[\"']" ; programmatic writes
   #"bash.*-c.*>"])                 ; nested shell redirects

(defn ^:private safe-for-plan-mode? [command-string]
  (let [cmd (-> command-string (string/split #"\s+") first)]
    (and (contains? plan-safe-commands-default cmd)
         (not (some #(re-find % command-string) plan-forbidden-patterns-default)))))

(defn ^:private shell-command [arguments {:keys [db config behavior]}]
  (let [command-args (get arguments "command")
        command (first (string/split command-args #"\s+"))
        user-work-dir (get arguments "working_directory")
        exclude-cmds (-> config :nativeTools :shell :excludeCommands set)
        plan-mode? (= "plan" behavior)]
    (or (tools.util/invalid-arguments arguments [["working_directory" #(or (nil? %)
                                                                           (fs/exists? %)) "working directory $working_directory does not exist"]
                                                 ["commmand" (constantly (not (contains? exclude-cmds command)))
                                                  (format "Command '%s' is excluded by configuration" command-args)]])
        ;; Check plan mode restrictions
        (when (and plan-mode? (not (safe-for-plan-mode? command-args)))
          {:error true
           :contents [{:type :text
                       :text "Command blocked in plan mode. Only read-only analysis commands are allowed."}]})
        (let [work-dir (or (some-> user-work-dir fs/canonicalize str)
                           (some-> (:workspace-folders db)
                                   first
                                   :uri
                                   shared/uri->filename)
                           (config/get-property "user.home"))
              _ (logger/debug logger-tag "Running command:" command-args)
              result (try
                       (p/shell {:dir work-dir
                                 :out :string
                                 :err :string
                                 :continue true} "bash -c" command-args)
                       (catch Exception e
                         {:exit 1 :err (.getMessage e)}))
              err (some-> (:err result) string/trim)
              out (some-> (:out result) string/trim)]
          (logger/debug logger-tag "Command executed:" result)
          (if (zero? (:exit result))
            (tools.util/single-text-content (:out result))
            {:error true
             :contents (remove nil?
                               (concat [{:type :text
                                         :text (str "Exit code " (:exit result))}]
                                       (when-not (string/blank? err)
                                         [{:type :text
                                           :text (str "Stderr:\n" err)}])
                                       (when-not (string/blank? out)
                                         [{:type :text
                                           :text (str "Stdout:\n" out)}])))})))))

(defn shell-command-summary [args]
  (if-let [command (get args "command")]
    (if (> (count command) 20)
      (format "Running '%s...'" (subs command 0 20))
      (format "Running '%s'" command))
    "Running shell command"))

(def definitions
  {"eca_shell_command"
   {:description (tools.util/read-tool-description "eca_shell_command")
    :parameters {:type "object"
                 :properties {"command" {:type "string"
                                         :description "The shell command to execute."}
                              "working_directory" {:type "string"
                                                   :description "The directory to run the command in. Default to the first workspace root."}}
                 :required ["command"]}
    :handler #'shell-command
    :require-approval-fn (fn [args {:keys [db]}]
                           (when-let [wd (and args (get args "working_directory"))]
                             (when-let [wd (and (fs/exists? wd) (str (fs/canonicalize wd)))]
                               (let [workspace-roots (mapv (comp shared/uri->filename :uri) (:workspace-folders db))]
                                 (not-any? #(fs/starts-with? wd %) workspace-roots)))))
    :summary-fn #'shell-command-summary}})
