(ns eca.shared
  (:require
   [camel-snake-kebab.core :as csk]
   [clojure.java.io :as io]
   [clojure.string :as string]
   [clojure.walk :as walk])
  (:import
   [java.net URI]
   [java.nio.file Paths]))

(set! *warn-on-reflection* true)

(def windows-os?
  (.contains (System/getProperty "os.name") "Windows"))

(def line-separator
  "The system's line separator."
  (System/lineSeparator))

(defn uri->filename [uri]
  (let [uri (URI. uri)]
    (-> uri Paths/get .toString
        ;; WINDOWS drive letters
        (string/replace #"^[a-z]:\\" string/upper-case))))

(defn filename->uri [^String filename]
  (let [uri (-> filename io/file .toPath .toUri .toString)
        [_match scheme+auth path] (re-matches #"([a-z:]+//.*?)(/.*)" uri)]
    (str scheme+auth
         (-> path
             (string/replace-first #"^/[a-zA-Z](?::|%3A)/"
                                   (if windows-os?
                                     string/upper-case
                                     string/lower-case))
             (string/replace ":" "%3A")))))

(defn update-last [coll f]
  (if (seq coll)
    (update coll (dec (count coll)) f)
    coll))

(defn deep-merge [v & vs]
  (letfn [(rec-merge [v1 v2]
            (if (and (map? v1) (map? v2))
              (merge-with deep-merge v1 v2)
              v2))]
    (when (some identity vs)
      (reduce #(rec-merge %1 %2) v vs))))

(defn assoc-some
  "Assoc[iate] if the value is not nil. "
  ([m k v]
   (if (nil? v) m (assoc m k v)))
  ([m k v & kvs]
   (let [ret (assoc-some m k v)]
     (if kvs
       (if (next kvs)
         (recur ret (first kvs) (second kvs) (nnext kvs))
         (throw (IllegalArgumentException.
                 "assoc-some expects even number of arguments after map/vector, found odd number")))
       ret))))

(defn update-some
  "Update if the value if not nil."
  ([m k f]
   (if-let [v (get m k)]
     (assoc m k (f v))
     m))
  ([m k f & args]
   (if-let [v (get m k)]
     (assoc m k (apply f v args))
     m)))

(defn multi-str [& strings] (string/join "\n" (remove nil? strings)))

(defn tokens->cost [input-tokens input-cache-creation-tokens input-cache-read-tokens output-tokens full-model db]
  (when-let [{:keys [input-token-cost output-token-cost
                     input-cache-creation-token-cost input-cache-read-token-cost]} (get-in db [:models full-model])]
    (when (and input-token-cost output-token-cost)
      (let [input-cost (* input-tokens input-token-cost)
            input-cost (if (and input-cache-creation-tokens input-cache-creation-token-cost)
                         (+ input-cost (* input-cache-creation-tokens input-cache-creation-token-cost))
                         input-cost)
            input-cost (if (and input-cache-read-tokens input-cache-read-token-cost)
                         (+ input-cost (* input-cache-read-tokens input-cache-read-token-cost))
                         input-cost)]
        (format "%.2f" (+ input-cost
                          (* output-tokens output-token-cost)))))))

(defn map->camel-cased-map [m]
  (let [f (fn [[k v]]
            (if (keyword? k)
              [(csk/->camelCase k) v]
              [k v]))]
    (walk/postwalk (fn [x]
                     (if (map? x)
                       (into {} (map f x))
                       x))
                   m)))

(defn obfuscate
  "Obfuscate all but first 3 and last 3 characters of a string, minimum 5 characters.
   If the string is 4 characters or less, obfuscate all characters.
   Replace the middle part with asterisks, but always at least 5 asterisks."
  [s]
  (when s
    (string/replace
      (if (<= (count s) 4)
        (apply str (repeat (count s) "*"))
        (str (subs s 0 3)
             (apply str (repeat (- (count s) 4) "*"))
             (subs s (- (count s) 3))))
      (string/join "" (repeat (- (count s) 4) "*")) "*****")))
