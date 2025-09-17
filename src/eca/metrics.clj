(ns eca.metrics
  (:require
   [eca.logger :as logger]))

(defn format-time-delta-ms [start-time end-time]
  (format "%.0fms" (float (/ (- end-time start-time) 1000000))))

(defn start-time->end-time-ms [start-time]
  (format-time-delta-ms start-time (System/nanoTime)))

(defn metrify-task [{:keys [task-id time]}]
  (logger/info (str task-id " " time)))

(defmacro task*
  "Executes `body` logging `message` formatted with the time spent
  from body."
  [task-id & body]
  (let [start-sym (gensym "start-time")]
    `(let [~start-sym (System/nanoTime)
           result# (do ~@body)]
       ~(with-meta
          `(metrify-task {:task-id ~task-id
                          :time (start-time->end-time-ms ~start-sym)})
          (meta &form))
       result#)))

(defmacro task [task-id & body]
  (with-meta `(task* ~task-id ~@body)
    (meta &form)))
