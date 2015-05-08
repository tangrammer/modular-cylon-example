(ns modular.cylon-oauth-example.http-kit
  (:require
   [schema.core :as s]
   [com.stuartsierra.component :as component]
   [clojure.tools.logging :refer :all]
   [modular.ring :refer (request-handler WebRequestHandler)]
   [org.httpkit.server :refer (run-server)]))

(def default-port 3000)

(defrecord Webserver [port]
  component/Lifecycle
  (start [this]
    (if-let [provider (first (filter #(satisfies? WebRequestHandler %) (vals this)))]
      (let [h (request-handler provider)]
        (assert h)
        (assoc this :h h))
      (throw (ex-info (format "http-kit module requires the existence of a component that satisfies %s" WebRequestHandler)
                      {:this this}))))

  (stop [this]
    (dissoc this :h)))


(defn start-server [system port ]
  (run-server (-> system :webserver :h) {:port port}))

(defn new-webserver [& {:as opts}]
  (let [{:keys [port]} (->> (merge {:port default-port} opts)
                            (s/validate {:port s/Int}))]
    (->Webserver port)
    ))
