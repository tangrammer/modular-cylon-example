(defproject cylon-oauth2-demo "0.1.0-SNAPSHOT"
  :description "A modular project using juxt/cylon oauth2"
  :url "http://github.com/tangrammer/modular-cylon-example"

  :exclusions [com.stuartsierra/component]

  :dependencies
  [
   [hiccup "1.0.5"]
   [com.stuartsierra/component "0.2.2"]
   [juxt.modular/bidi "0.6.1"]
   [juxt.modular/clostache "0.6.0"]

   [juxt.modular/bootstrap "0.2.0" :exclusions [cylon]]

   [juxt.modular/http-kit "0.5.3"]
   [juxt.modular/maker "0.5.0"]
   [juxt.modular/wire-up "0.5.0"]
   [com.datomic/datomic-free "0.9.4699"]
   [juxt.modular/datomic "0.2.1" :exclusions [com.datomic/datomic-free]]


   [tangrammer/co-dependency "0.1.6-SNAPSHOT"]
   [org.clojure/clojure "1.7.0-alpha4"]
   [org.clojure/tools.logging "0.2.6"]
   [org.clojure/tools.reader "0.8.9"]
   [org.slf4j/jcl-over-slf4j "1.7.2"]
   [org.slf4j/jul-to-slf4j "1.7.2"]
   [org.slf4j/log4j-over-slf4j "1.7.2"]
   [org.webjars/bootstrap "3.3.0"]
   [org.webjars/jquery "2.1.0"]
   [prismatic/plumbing "0.2.2"]
   [prismatic/schema "0.3.3"]
   [ch.qos.logback/logback-classic "1.0.7" :exclusions [org.slf4j/slf4j-api]]

   [cylon "0.5.0-20150120.154532-33" :exclusions [org.clojure/clojure com.stuartsierra/component]]
   [liberator "0.11.0"  :exclusions [org.clojure/tools.logging org.clojure/tools.trace]]
   ;; email
   [com.draines/postal "1.11.1"]


   [garden "1.1.5" :exclusions [org.clojure/clojure org.clojure/clojurescript]]
   [hiccup "1.0.5"]


   ]

  :main modular.cylon-oauth-example.main

  :repl-options {:init-ns user
                 :welcome (println "Type (dev) to start")}

  :profiles {:dev {:dependencies [[org.clojure/tools.namespace "0.2.5"]


                                  [milesian/system-diagrams "0.1.2" ]

                                  [milesian/bigbang "0.1.1"]
                                  [milesian/aop "0.1.4"]
                                  [milesian/system-examples "0.1.1-SNAPSHOT"]
                                  [milesian/identity "0.1.4-SNAPSHOT"]

                                  ]
                   :source-paths ["dev"]}})
