(ns modular.cylon-oauth-example.main
  "Main entry point"
  (:require
            [modular.cylon-oauth-example.system :refer (new-production-system)]
            [tangrammer.component.co-dependency :as co-dependency]
            [milesian.bigbang :as bigbang]
            [milesian.identity :as identity]
            [modular.ring :refer (request-handler)]
            [milesian.aop :as aop]
            [milesian.aop.utils  :refer (extract-data)]
            [milesian.sequence-diagram :refer (store-message try-to-publish store)]

;            [org.httpkit.server :refer (run-server)]
            ))

(declare system)
(declare app)
(defn diagram
  "to get sequence diagram we need the ->start-fn-call and
  the <-return-fn-call times of the fn invocation call.
  The sequence will be published if all fns are finished (:closed)"
  [*fn* this & args]
  (let [invocation-data (extract-data *fn* this args)]
    (let [res (apply *fn* (conj args this))]
      (println invocation-data)
      res)))

(defn start-dev
  "Starts the current development system."
  [system]
  (let [future-system (atom system)]
    (#(bigbang/expand % {:before-start [[identity/add-meta-key %]
                                                                [identity/assoc-meta-who-to-deps]
                                                                [co-dependency/assoc-co-dependencies future-system]]
                                                 :after-start [[aop/wrap diagram]
                                                               [co-dependency/update-atom-system future-system]]})
     system)

    ))

(defn main [& args]
  ;; We eval so that we don't AOT anything beyond this class

  (println "Starting modular.cylon-oauth-example")
  (let [system (-> (new-production-system)
                   start-dev)]


    (println "System started")
    (println "Ready...")
    (def app (request-handler (-> system :modular-bidi-router-webrouter)))
    (def system system)
    (comment (require '[org.httpkit.server :refer (run-server)])
             (def s (run-server app {:port 8010})))
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
