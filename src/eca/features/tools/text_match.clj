(ns eca.features.tools.text-match
  (:require [clojure.string :as string]
            [eca.logger :as logger])
  (:import
   [java.util.regex Pattern]))

(set! *warn-on-reflection* true)

(defn normalize-for-matching
  "Normalize content for matching: fix line endings and trim trailing whitespace.
   This handles the most common differences between LLM-generated content and files."
  [s]
  (-> s
      (string/replace #"\r\n|\r" "\n")       ; CRLF/CR -> LF
      (string/replace #"(?m)[ \t]+$" "")))

(defn- apply-replacement
  "Apply string replacement, handling all? flag for replacing all occurrences vs first only."
  [content search replacement all?]
  (if all?
    (string/replace content search replacement)
    (string/replace-first content search replacement)))

(defn- try-exact-match
  "Attempt exact string matching without normalization.
   Returns result map if successful, nil if no match found."
  [file-content original-content new-content all?]
  (when (string/includes? file-content original-content)
    {:original-full-content file-content
     :new-full-content (apply-replacement file-content original-content new-content all?)
     :normalized? false}))

(defn- count-normalized-matches
  "Count how many times the normalized search content appears in normalized file content."
  [normalized-file-content normalized-search-content]
  (count (re-seq (re-pattern (Pattern/quote normalized-search-content)) normalized-file-content)))

(defn- try-normalized-match
  "Attempt normalized matching when exact matching fails.
   Returns result map with success/error status."
  [file-content original-content new-content all? path]
  (let [normalized-file-content (normalize-for-matching file-content)
        normalized-search-content (normalize-for-matching original-content)
        normalized-new-content (normalize-for-matching new-content)
        match-count (count-normalized-matches normalized-file-content normalized-search-content)]
    (cond
      (= match-count 0)
      (do
        (logger/debug "Content not found in" path)
        {:error :not-found
         :original-full-content file-content})

      (and (> match-count 1) (not all?))
      {:error :ambiguous
       :match-count match-count
       :original-full-content file-content}

      :else
      (do
        (logger/debug "Content matched using normalization for" path
                      "- match count:" match-count
                      "- replacing all:" all?)
        ;; Try original replacement first, fall back to normalized if no change
        (let [original-attempt (apply-replacement file-content original-content new-content all?)]
          (if (= original-attempt file-content)
            ;; Original replacement failed, use normalized
            {:original-full-content file-content
             :new-full-content (apply-replacement normalized-file-content
                                                  normalized-search-content
                                                  normalized-new-content
                                                  all?)
             :normalized? true}
            ;; Original replacement succeeded
            {:original-full-content file-content
             :new-full-content original-attempt
             :normalized? false}))))))

(defn apply-content-change-to-string
  "Apply content change to a string without file I/O.

   Takes file content as string and attempts to replace original-content with new-content.
   First tries exact matching, falls back to normalized matching if that fails."
  [file-content original-content new-content all? path]
  (or (try-exact-match file-content original-content new-content all?)
      (try-normalized-match file-content original-content new-content all? path)))

(defn apply-content-change-to-file
  "Apply content change to a file by path.
   Reads file content and delegates to apply-content-change-to-string."
  [path original-content new-content all?]
  (let [file-content (slurp path)]
    (apply-content-change-to-string file-content original-content new-content all? path)))
