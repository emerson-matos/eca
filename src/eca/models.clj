(ns eca.models
  (:require
   [cheshire.core :as json]
   [eca.logger :as logger]
   [eca.shared :refer [assoc-some]]))

(set! *warn-on-reflection* true)

(def ^:private logger-tag "[MODELS]")

(defn ^:private models-dev* []
  (try
    (let [response (slurp "https://models.dev/api.json")
          data (json/parse-string response)]
      data)
    (catch Exception e
      (logger/error logger-tag " Error fetching models from models.dev:" (.getMessage e))
      {})))

(def ^:private models-dev (memoize models-dev*))

(def ^:private one-million 1000000)

(def ^:private models-with-web-search-support
  #{"openai/gpt-4.1"
    "openai/gpt-5"
    "openai/gpt-5-mini"
    "openai/gpt-5-nano"
    "anthropic/claude-sonnet-4-20250514"
    "anthropic/claude-opus-4-20250514"
    "anthropic/claude-opus-4-1-20250805"
    "anthropic/claude-3-5-haiku-20241022"})

(defn all
  "Return all known existing models with their capabilities and configs."
  []
  (reduce
   (fn [m [provider provider-config]]
     (merge m
            (reduce
             (fn [p [model model-config]]
               (assoc p (str provider "/" model)
                      (assoc-some
                       {:reason? (get model-config "reasoning")
                        ;; TODO how to check for web-search mode dynamically,
                        ;; maybe fixed after web-search toolcall is implemented
                        :web-search (contains? models-with-web-search-support (name model))
                        :tools (get model-config "tool_call")
                        :max-output-tokens (get-in model-config ["limit" "output"])}
                       :limit {:context (get-in model-config ["limit" "context"])
                               :output (get-in model-config ["limit" "output"])}
                       :input-token-cost (some-> (get-in model-config ["cost" "input"]) float (/ one-million))
                       :output-token-cost (some-> (get-in model-config ["cost" "output"]) float (/ one-million))
                       :input-cache-creation-token-cost (some-> (get-in model-config ["cost" "cache_write"]) float (/ one-million))
                       :input-cache-read-token-cost (some-> (get-in model-config ["cost" "cache_read"]) float (/ one-million)))))
             {}
             (get provider-config "models"))))
   {}
   (models-dev)))

(comment
  (require '[clojure.pprint :as pprint])
  (pprint/pprint (models-dev))
  (pprint/pprint (all)))
