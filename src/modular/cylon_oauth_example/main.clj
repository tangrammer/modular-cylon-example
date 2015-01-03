(ns modular.cylon-oauth-example.main
  "Main entry point"
  (:require clojure.pprint)
  (:gen-class))

(defn -main [& args]
  ;; We eval so that we don't AOT anything beyond this class
  (eval '(do (require 'modular.cylon-oauth-example.system)
             (require 'modular.cylon-oauth-example.main)
             (require 'com.stuartsierra.component)
             (require 'tangrammer.component.co-dependency)

             (require 'clojure.java.browse)

             (println "Starting modular.cylon-oauth-example")

             (let [system (->
                           (modular.cylon-oauth-example.system/new-production-system)
                           tangrammer.component.co-dependency/start-system)]

               (println "System started")
               (println "Ready...")

               (let [url (format "http://localhost:%d/" (-> system :http-listener-listener :port))]
                 (println (format "Browsing at %s" url))
                 (clojure.java.browse/browse-url url))))))
