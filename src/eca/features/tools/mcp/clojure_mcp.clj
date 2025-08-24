(ns eca.features.tools.mcp.clojure-mcp
  (:require [clojure.string :as string]
            [eca.diff :as diff]
            [eca.features.tools.util :as tools.util]))

(defmethod tools.util/tool-call-details-after-invocation :clojure_edit [name arguments details result]
  (tools.util/tool-call-details-after-invocation :file_edit arguments details result))

(defmethod tools.util/tool-call-details-after-invocation :clojure_edit_replace_sexp [name arguments details result]
  (tools.util/tool-call-details-after-invocation :file_edit arguments details result))

(defmethod tools.util/tool-call-details-after-invocation :file_edit [name arguments details result]
  (when-not (:error result)
    (when-let [diff (some->> result :contents (filter #(= :text (:type %))) first :text)]
      (let [{:keys [added removed]} (diff/unified-diff-counts diff)]
        {:type :fileChange
         :path (get arguments "file_path")
         :linesAdded added
         :linesRemoved removed
         :diff diff}))))

(defmethod tools.util/tool-call-details-after-invocation :file_write [name arguments details result]
  (when-not (:error result)
    (when-let [diff (some->> result :contents
                             (filter #(= :text (:type %)))
                             first :text
                             (string/split-lines)
                             (drop 2)
                             (string/join "\n"))]
      (let [{:keys [added removed]} (diff/unified-diff-counts diff)]
        {:type :fileChange
         :path (get arguments "file_path")
         :linesAdded added
         :linesRemoved removed
         :diff diff}))))
