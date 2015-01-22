(ns modular.cylon-oauth-example.main
  "Main entry point"
  (:require
            [modular.cylon-oauth-example.system :refer (new-production-system)]
            [tangrammer.component.co-dependency :refer (start-system)]
;            [org.httpkit.server :refer (run-server)]
            ))

(declare system)
(declare app)
(defn main [& args]
  ;; We eval so that we don't AOT anything beyond this class

  (println "Starting modular.cylon-oauth-example")
  (let [system (-> (new-production-system)
                   start-system)]


    (println "System started")
    (println "Ready...")
    (def app (.request-handler (-> system :modular-bidi-router-webrouter)))
    (def system system)

    ;;               (run-server app {:port 8010})
    #_(let [url (format "http://localhost:%d/" (-> #'system :http-listener-listener :port))]
        (println (format "Browsing at %s" url))
        (clojure.java.browse/browse-url url))))


(defn init []
  (main)
  (println app)
   (println system)
  )
(defn destroy []
  (.stop system))
