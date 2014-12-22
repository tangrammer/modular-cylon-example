(defproject foo "0.1.0-SNAPSHOT"
  :description "A modular project created with lein new modular bootstrap-cover"
  :url "http://github.com/tangrammer/foo"

  :exclusions [com.stuartsierra/component]

  :dependencies
  [
   [hiccup "1.0.5"]
   [com.stuartsierra/component "0.2.2"]
   [juxt.modular/bidi "0.6.1"]
   [juxt.modular/clostache "0.6.0"]
   [juxt.modular/http-kit "0.5.3"]
   [juxt.modular/maker "0.5.0"]
   [juxt.modular/wire-up "0.5.0"]
   [malcolmsparks/co-dependency "0.1.5"]
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

   ;;   [cylon "0.5.0-20141113.002430-27"]
   [cheshire                "5.3.1"]
   [clj-jwt "0.0.8"]
   [liberator "0.11.0"  :exclusions [org.clojure/tools.logging org.clojure/tools.trace]]
   ;; email
   [com.draines/postal "1.11.1"]


   [garden "1.1.5" :exclusions [org.clojure/clojure]]
   [hiccup "1.0.5"]

 ;  [cylon/bootstrap-login-form "0.1.0-SNAPSHOT"]

   ]

  :main foo.main

  :repl-options {:init-ns user
                 :welcome (println "Type (dev) to start")}

  :profiles {:dev {:dependencies [[org.clojure/tools.namespace "0.2.5"]

                                  ]
                   :source-paths ["dev"
                                  ]}})
