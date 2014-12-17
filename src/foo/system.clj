(ns foo.system
  "Components and their dependency relationships"
  (:refer-clojure :exclude (read))
  (:require
   [clojure.java.io :as io]
   [clojure.tools.reader :refer (read)]
   [clojure.string :as str]
   [clojure.tools.reader.reader-types :refer (indexing-push-back-reader)]
   [com.stuartsierra.component :refer (system-map system-using using)]
   [tangrammer.component.co-dependency :refer (co-using system-co-using)]
   [modular.maker :refer (make)]
   [foo.website :refer (new-website)]
   [modular.bidi :refer (new-router new-static-resource-service)]
   [modular.clostache :refer (new-clostache-templater)]
   [modular.http-kit :refer (new-webserver)]))

(defn ^:private read-file
  [f]
  (read
   ;; This indexing-push-back-reader gives better information if the
   ;; file is misconfigured.
   (indexing-push-back-reader
    (java.io.PushbackReader. (io/reader f)))))

(defn ^:private config-from
  [f]
  (if (.exists f)
    (read-file f)
    {}))

(defn ^:private user-config
  []
  (config-from (io/file (System/getProperty "user.home") ".foo.edn")))

(defn ^:private config-from-classpath
  []
  (if-let [res (io/resource "foo.edn")]
    (config-from (io/file res))
    {}))

(defn config
  "Return a map of the static configuration used in the component
  constructors."
  []
  (merge (config-from-classpath)
         (user-config)))



(defn http-listener-components [system config]
  (assoc system
    :http-listener-listener
    (->
      (make new-webserver config)
      (using [])
      (co-using []))))

(defn modular-bidi-router-components [system config]
  (assoc system
    :modular-bidi-router-webrouter
    (->
      (make new-router config)
      (using [])
      (co-using []))))


(defn clostache-templater-components [system config]
  (assoc system
    :clostache-templater-templater
    (->
      (make new-clostache-templater config)
      (using [])
      (co-using []))))

(defn public-resources-components [system config]
  (assoc system
    :public-resources-public-resources
    (->
      (make new-static-resource-service config :uri-context "/static" :resource-prefix "public")
      (using [])
      (co-using []))))

(defn bootstrap-cover-website-components [system config]
  (assoc system
    :bootstrap-cover-website-website
    (->
      (make new-website config)
      (using [])
      (co-using []))))

(defn twitter-bootstrap-components [system config]
  (assoc system
    :twitter-bootstrap-service
    (->
      (make new-static-resource-service config :uri-context "/bootstrap" :resource-prefix "META-INF/resources/webjars/bootstrap/3.3.0")
      (using [])
      (co-using []))))


(defn jquery-components [system config]
  (assoc system
    :jquery-resources
    (->
      (make new-static-resource-service config :uri-context "/jquery" :resource-prefix "META-INF/resources/webjars/jquery/2.1.0")
      (using [])
      (co-using []))))

(defn new-system-map
  [config]
  (apply system-map
    (apply concat
      (-> {}
          
          (http-listener-components config)
          (modular-bidi-router-components config)
          (clostache-templater-components config)
          (public-resources-components config)
          (bootstrap-cover-website-components config)
          (twitter-bootstrap-components config)
          (jquery-components config)))))

(defn new-dependency-map
  []
  {:http-listener-listener {:request-handler :modular-bidi-router-webrouter}, :modular-bidi-router-webrouter {:public-resources :public-resources-public-resources, :website :bootstrap-cover-website-website, :twitter-bootstrap :twitter-bootstrap-service, :jquery :jquery-resources}, :bootstrap-cover-website-website {:templater :clostache-templater-templater}})

(defn new-co-dependency-map
  []
  {:bootstrap-cover-website-website {:router :modular-bidi-router-webrouter}})

(defn new-production-system
  "Create the production system"
  []
  (-> (new-system-map (config))
      (system-using (new-dependency-map))
      (system-co-using (new-co-dependency-map))))
