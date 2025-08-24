(ns eca.proxy
  (:require
   [eca.config :as config])
  (:import
   [java.net URL]))

(set! *warn-on-reflection* true)

(defn load! []
  (when-let [^URL url (try (some-> (not-empty (config/get-env "HTTP_PROXY"))
                                   (URL.))
                           (catch Exception _ nil))]
    (System/setProperty "http.proxyHost" (.getHost url))
    (let [port (.getPort url)]
      (when (not= -1 port)
        (System/setProperty "http.proxyPort" (str (.getPort url))))))
  (when-let [^URL url (try (some-> (not-empty (config/get-env "HTTPS_PROXY"))
                                   (URL.))
                           (catch Exception _ nil))]
    (System/setProperty "https.proxyHost" (.getHost url))
    (let [port (.getPort url)]
      (when (not= -1 port)
        (System/setProperty "https.proxyPort" (str (.getPort url)))))))
