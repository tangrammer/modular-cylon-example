(ns modular.cylon-oauth-example.system
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
   [modular.cylon-oauth-example.website :refer (new-website)]
   [modular.cylon-oauth-example.user-store :refer (new-user-store)]
   [modular.cylon-oauth-example.emailer :refer (new-emailer)]

   [clojure.tools.logging :refer :all]
   [cylon.token-store.atom-backed-store :refer (new-atom-backed-token-store)]
   [cylon.session.cookie-session-store :refer (new-cookie-session-store)]
   [cylon.password.pbkdf2 :refer (new-pbkdf2-hash)]
   [cylon.password :refer (new-durable-password-verifier)]
   [cylon.user.login :refer (new-login)]
   [cylon.oauth.server.logout :refer (new-logout)]
   [cylon.user.reset-password :refer (new-reset-password)]
   [modular.cylon-oauth-example.forms :refer (new-user-form-renderer)]
   [cylon.oauth.registry.ref-backed-registry :refer (new-ref-backed-client-registry)]
   [cylon.user.signup :refer (new-signup-with-totp)]
   [cylon.event :refer (EventPublisher)]

   [cylon.oauth.server.server :refer (new-authorization-server)]

   [cylon.oauth.client.web-client :refer (new-web-client)]

   [modular.bidi :refer (new-router new-static-resource-service new-web-service)]
   [modular.clostache :refer (new-clostache-templater)]
;   [modular.http-kit :refer (new-webserver)]
   ))

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
  (config-from (io/file (System/getProperty "user.home") ".modular.edn")))

(defn ^:private config-from-classpath
  []
  (if-let [res (io/resource "modular.edn")]
    (config-from (io/file res))
    {}))

(defn config
  "Return a map of the static configuration used in the component
  constructors."
  []
  (merge (config-from-classpath)
         (user-config)))

#_(defn http-listener-components [system config]
  (assoc system
    :http-listener-listener
    (->
     (make new-webserver config :port (get-in config [:webapp :port]))
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
     (make new-website config :signup-uri (str (get-in config [:auth-server :location]) "/auth/signup"))
     (using {:oauth-client :webapp-oauth-client})
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

(defn add-oauth-client
  "Add a web application.

  Web applications are OAuth2 clients."
  [system config]
  (assoc system
    ;; TODO: Why doesn't web app need :web-resources? Is it duplicating
    ;; these routes?
    :oauth-client-registry (new-ref-backed-client-registry)

    ;; The webapp establishes long running sessions with its users,
    ;; represented by durable tokens stored in PostgreSQL.
    :webapp-token-store (new-atom-backed-token-store)

    :webapp-session-store
    (-> (new-cookie-session-store :cookie-id "webapp-session-id")
        (using {:token-store :webapp-token-store}))

    ;; Clients generate state tokens to ensure the authenticity of the
    ;; authorization server that contact it.
    ;; We'll give 60 minutes to login, the reason for this is in case
    ;; the user has problems and needs to do a password reset.
    :state-store
    (new-atom-backed-token-store :ttl-in-secs (* 60 60))

    ;; The webapp defines the configuration of an OAuth2 client, which
    ;; it can then use to determine a user's identity and authorization
    ;; rights.
    ;;
    :webapp-oauth-client
    (-> (new-web-client
         :application-name "Demo Cylon OAuth"
         :homepage-uri "https://demo-cylon-oauth.com"

         :uri-context ""
         :redirection-uri (str (get-in config [:webapp :location]) "/grant")
         :post-logout-redirect-uri (str (get-in config [:webapp :location]) "/")
         :client-id (get-in config [:oauth-client :id])
         :client-secret (get-in config [:oauth-client :secret])
         :required-scopes (get-in config [:oauth-client :scopes])

         ;; Perhaps we could get these during dynamic registration with
         ;; the client-registry?
         :authorize-uri (get-in config [:oauth-client :authorize-uri])
;         (str (get-in config [:auth-server :location]) "/auth/authorize")

         :access-token-uri (get-in config [:oauth-client :access-token-uri])
;         (str (get-in config [:auth-server :location]) "/auth/access-token")

         :end-session-endpoint
         (str (get-in config [:auth-server :location]) "/auth/logout")

         ;; Specify this client is special doesn't require the user to
         ;; authorize the application. This should only be false for
         ;; standard applications, and always set to true
         ;; for third-party ones.
         :requires-user-acceptance? false)

        (using
         { ;; Clients auto-register if :client-registry is specified.
          :client-registry :oauth-client-registry
          :state-store :state-store
          :session-store :webapp-session-store}))

    ))

(defn new-system-map
  [config]
  (apply system-map
    (apply concat
      (-> {}
          (add-oauth-client config)
;          (http-listener-components config)
          (modular-bidi-router-components config)
          (clostache-templater-components config)
          (public-resources-components config)
          (bootstrap-cover-website-components config)
          (twitter-bootstrap-components config)
          (jquery-components config)

          ))))

(defn new-dependency-map
  []
  {#_:http-listener-listener #_{:request-handler :modular-bidi-router-webrouter},
   :modular-bidi-router-webrouter {:public-resources :public-resources-public-resources,
                                   :website :bootstrap-cover-website-website,
                                   :twitter-bootstrap :twitter-bootstrap-service,
                                   :jquery :jquery-resources
                                   :oauth-client :webapp-oauth-client
                                   },
   :bootstrap-cover-website-website {:templater :clostache-templater-templater}})

(defn new-co-dependency-map
  []
  {:bootstrap-cover-website-website {:router :modular-bidi-router-webrouter}})

(defn new-production-system
  "Create the production system"
  []
  (-> (new-system-map (config))
      (system-using (new-dependency-map))
      (system-co-using (new-co-dependency-map))))
