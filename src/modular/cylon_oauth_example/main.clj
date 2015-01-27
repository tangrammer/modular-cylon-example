(ns modular.cylon-oauth-example.main
  "Main entry point"
  (:require
            [modular.cylon-oauth-example.system :refer (new-production-system)]
            [tangrammer.component.co-dependency :refer (start-system)]
            [modular.ring :refer (request-handler)]))

(declare system)
(declare app)
(defn main [& args]
  ;; We eval so that we don't AOT anything beyond this class

  (println "Starting modular.cylon-oauth-example")
  (let [system (-> (new-production-system)
                   start-system)]


    (println "System started")
    (println "Ready...")
    (def app (request-handler (-> system :modular-bidi-router-webrouter)))
    (def system system)))


(defn init []
  (main)
  (println app)
   (println system)
  )
(defn destroy []
  (.stop system))
