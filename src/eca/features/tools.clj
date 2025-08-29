(ns eca.features.tools
  "This ns centralizes all available tools for LLMs including
   eca native tools and MCP servers."
  (:require
   [clojure.string :as string]
   [eca.features.tools.editor :as f.tools.editor]
   [eca.features.tools.filesystem :as f.tools.filesystem]
   [eca.features.tools.mcp :as f.mcp]
   [eca.features.tools.mcp.clojure-mcp]
   [eca.features.tools.shell :as f.tools.shell]
   [eca.features.tools.util :as tools.util]
   [eca.logger :as logger]
   [eca.messenger :as messenger]
   [eca.shared :refer [assoc-some]])
  (:import
   [java.util Map]))

(set! *warn-on-reflection* true)

(def ^:private logger-tag "[TOOLS]")

(defn ^:private native-definitions [db config]
  (into
   {}
   (map (fn [[name tool]]
          [name (-> tool
                    (assoc :name name)
                    (update :description #(-> %
                                              (string/replace #"\$workspaceRoots" (constantly (tools.util/workspace-roots-strs db))))))]))
   (merge {}
          (when (get-in config [:nativeTools :filesystem :enabled])
            f.tools.filesystem/definitions)
          (when (get-in config [:nativeTools :shell :enabled])
            f.tools.shell/definitions)
          (when (get-in config [:nativeTools :editor :enabled])
            f.tools.editor/definitions))))

(defn ^:private native-tools [db config]
  (vals (native-definitions db config)))

(defn all-tools
  "Returns all available tools, including both native ECA tools
   (like filesystem and shell tools) and tools provided by MCP servers."
  [behavior db config]
  (let [disabled-tools (set (get-in config [:disabledTools] []))]
    (filterv
     (fn [tool]
       (and (not (contains? disabled-tools (:name tool)))
            ;; check for enabled-fn if present
            ((or (:enabled-fn tool) (constantly true))
             {:behavior behavior
              :db db
              :config config})))
     (concat
      (mapv #(assoc % :origin :native) (native-tools db config))
      (mapv #(assoc % :origin :mcp) (f.mcp/all-tools db))))))

(defn call-tool! [^String name ^Map arguments db config messenger behavior]
  (logger/info logger-tag (format "Calling tool '%s' with args '%s'" name arguments))
  (let [arguments (update-keys arguments clojure.core/name)]
    (try
      (let [result (if-let [native-tool-handler (get-in (native-definitions db config) [name :handler])]
                     (native-tool-handler arguments {:db db
                                                     :config config
                                                     :messenger messenger
                                                     :behavior behavior})
                     (f.mcp/call-tool! name arguments db))]
        (logger/debug logger-tag "Tool call result: " result)
        result)
      (catch Exception e
        (logger/warn logger-tag (format "Error calling tool %s: %s\n%s" name (.getMessage e) (with-out-str (.printStackTrace e))))
        {:error true
         :contents [{:type :text
                     :text (str "Error calling tool: " (.getMessage e))}]}))))

(defn init-servers! [db* messenger config]
  (let [disabled-tools (set (get-in config [:disabledTools] []))
        with-tool-status (fn [tool]
                           (assoc-some tool :disabled (contains? disabled-tools (:name tool))))]
    (messenger/tool-server-updated messenger {:type :native
                                              :name "ECA"
                                              :status "running"
                                              :tools (->> (native-tools @db* config)
                                                          (mapv #(select-keys % [:name :description :parameters]))
                                                          (mapv with-tool-status))})
    (f.mcp/initialize-servers-async!
     {:on-server-updated (fn [server]
                           (messenger/tool-server-updated messenger (-> server
                                                                        (assoc :type :mcp)
                                                                        (update :tools #(mapv with-tool-status %)))))}
     db*
     config)))

(defn stop-server! [name db* messenger config]
  (let [disabled-tools (set (get-in config [:disabledTools] []))
        with-tool-status (fn [tool]
                           (assoc-some tool :disabled (contains? disabled-tools (:name tool))))]
    (f.mcp/stop-server!
     name
     db*
     config
     {:on-server-updated (fn [server]
                           (messenger/tool-server-updated messenger (-> server
                                                                        (assoc :type :mcp)
                                                                        (update :tools #(mapv with-tool-status %)))))})))

(defn start-server! [name db* messenger config]
  (let [disabled-tools (set (get-in config [:disabledTools] []))
        with-tool-status (fn [tool]
                           (assoc-some tool :disabled (contains? disabled-tools (:name tool))))]
    (f.mcp/start-server!
     name
     db*
     config
     {:on-server-updated (fn [server]
                           (messenger/tool-server-updated messenger (-> server
                                                                        (assoc :type :mcp)
                                                                        (update :tools #(mapv with-tool-status %)))))})))

(defn legacy-manual-approval? [config]
  (let [manual-approval? (get-in config [:toolCall :manualApproval] nil)]
    (if (coll? manual-approval?)
      (some #(= name (str %)) manual-approval?)
      manual-approval?)))

(defn manual-approval? [all-tools name args db config]
  (boolean
    (let [require-approval-fn (:require-approval-fn (first (filter #(= name (:name %))
                                                                   all-tools)))
          {:keys [allow ask]} (get-in config [:toolCall :approval])]
      (cond
        (and require-approval-fn (require-approval-fn args {:db db}))
        true

        (some #(= name (str (first %))) ask)
        true

        (some #(= name (str (first %))) allow)
        false

        (legacy-manual-approval? config)
        true

        :else
        false))))

(defn tool-call-summary [all-tools name args]
  (when-let [summary-fn (:summary-fn (first (filter #(= name (:name %))
                                                    all-tools)))]
    (try
      (summary-fn args)
      (catch Exception e
        (logger/error (format "Error in tool call summary fn %s: %s" name (.getMessage e)))
        nil))))

(defn tool-call-details-before-invocation
  "Return the tool call details before invoking the tool."
  [name arguments]
  (tools.util/tool-call-details-before-invocation name arguments))

(defn tool-call-details-after-invocation
  "Return the tool call details after invoking the tool."
  [name arguments details result]
  (tools.util/tool-call-details-after-invocation name arguments details result))
