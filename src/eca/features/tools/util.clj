(ns eca.features.tools.util
  (:require
   [clojure.java.shell :as shell]
   [clojure.string :as string]
   [eca.shared :as shared]))

(defmulti tool-call-details-before-invocation
  "Return the tool call details before invoking the tool."
  (fn [name arguments] (keyword name)))

(defmethod tool-call-details-before-invocation :default [name arguments]
  nil)

(defmulti tool-call-details-after-invocation
  "Return the tool call details after invoking the tool."
  (fn [name arguments details result] (keyword name)))

(defmethod tool-call-details-after-invocation :default [name arguments details result]
  details)

(defn single-text-content [text & [error]]
  {:error (boolean error)
   :contents [{:type :text
               :text text}]})

(defn workspace-roots-strs [db]
  (->> (:workspace-folders db)
       (map #(shared/uri->filename (:uri %)))
       (string/join "\n")))

(defn command-available? [command & args]
  (try
    (zero? (:exit (apply shell/sh (concat [command] args))))
    (catch Exception _ false)))

(defn invalid-arguments [arguments validator]
  (first (keep (fn [[key pred error-msg]]
                 (let [value (get arguments key)]
                   (when-not (pred value)
                     (single-text-content (string/replace error-msg (str "$" key) (str value))
                                          :error))))
               validator)))
