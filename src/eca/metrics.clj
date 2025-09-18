(ns eca.metrics
  (:require
   [clojure.string :as string]
   [eca.logger :as logger]
   [eca.shared :as shared]
   [eca.config :as config]))

(defprotocol IMetrics
  (start! [this])
  (count! [this name value attrs]))

(defrecord NoopMetrics [db*]
  IMetrics
  (start! [_])
  (count! [_ _ _ _]))

(defn default-attrs [db*]
  {:hostname (shared/hostname)
   :client-name (:name (:client-info @db*))
   :client-version (:version (:client-info @db*))
   :server-version (config/eca-version)
   :os-name (System/getProperty "os.name")
   :os-version (System/getProperty "os.version")
   :os-arch (System/getProperty "os.arch")
   :workspace-roots (string/join ", " (map (comp shared/uri->filename :uri)
                                           (:workspace-folders @db*)))})

(defn format-time-delta-ms [start-time end-time]
  (format "%.0fms" (float (/ (- end-time start-time) 1000000))))

(defn start-time->end-time-ms [start-time]
  (format-time-delta-ms start-time (System/nanoTime)))

(defn metrify-task [{:keys [task-id metrics time]}]
  (logger/info (str task-id " " time))
  (count! metrics (str "task-" (name task-id)) 1 (default-attrs (:db* metrics))))

(defmacro task*
  "Executes `body` logging `message` formatted with the time spent
  from body."
  [metrics task-id & body]
  (let [start-sym (gensym "start-time")]
    `(let [~start-sym (System/nanoTime)
           result# (do ~@body)]
       ~(with-meta
          `(metrify-task {:task-id ~task-id
                          :metrics ~metrics
                          :time (start-time->end-time-ms ~start-sym)})
          (meta &form))
       result#)))

(defmacro task [metrics task-id & body]
  (with-meta `(task* ~metrics ~task-id ~@body)
    (meta &form)))

(defn count-up! [name extra-attrs metrics]
  (count! metrics name 1 (merge (default-attrs (:db* metrics))
                                extra-attrs)))
