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
   [foo.user-store :refer (new-user-store)]
   [foo.emailer :refer (new-emailer)]

   [clojure.tools.logging :refer :all]
   [cylon.token-store.atom-backed-store :refer (new-atom-backed-token-store)]
   [cylon.session.cookie-session-store :refer (new-cookie-session-store)]
   [cylon.password.pbkdf2 :refer (new-pbkdf2-hash)]
   [cylon.password :refer (new-durable-password-verifier)]
   [cylon.user.login :refer (new-login)]
   [cylon.oauth.server.logout :refer (new-logout)]
   [cylon.user.reset-password :refer (new-reset-password)]
   [foo.forms :refer (new-user-form-renderer)]
   [cylon.oauth.registry.ref-backed-registry :refer (new-ref-backed-client-registry)]
   [cylon.user.signup :refer (new-signup-with-totp)]
   [cylon.event :refer (EventPublisher)]

   [cylon.oauth.server.server :refer (new-authorization-server)]

   [cylon.oauth.client.web-client :refer (new-web-client)]

   [modular.bidi :refer (new-router new-static-resource-service new-web-service)]
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
  (config-from (io/file (System/getProperty "user.home") ".modular.edn")))

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
      (make new-website config)
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

(defn add-user-store [system config]
  (assoc system
    :user-token-store
    (new-atom-backed-token-store
     :ttl-in-secs nil)
    :user-store (-> (new-user-store)
                    (using {:token-store :user-token-store} ))

    ))

(defn add-emailer [system config]
  ;; Emails are sent via this component
  (assoc system
        :emailer  (new-emailer :settings (get config :sendgrid))))


(defn add-authorization-server
  "Add an OAuth2 authorization server. These components handle a wide
  range of authentication and authorization concerns, including user
  authentication (possibly multi-factor), user sign-up, password
  verification and reset, email verification, scopes, issuing access
  tokens and authorizing clients to use resources on behalf of
  users.

  See RFC-6749 for a more details on OAuth2."
  [system config]
  (assoc system

    ;; We create a token store to track logged-in users.
    :authorization-server-token-store
    (new-atom-backed-token-store
     :ttl-in-secs (* 60 60 24))

    ;; We layer on a session store, using HTTP cookies to keep users
    ;; logged in.
    :authorization-server-session-store
    (-> (new-cookie-session-store :cookie-id "authorization-server-session-id")
        (using {:token-store :authorization-server-token-store}))


    ;; We now specify the password hashing algorithm
    :password-hash-algo (new-pbkdf2-hash)

    ;; The durable password verifier is responsible for storing and
    ;; verifying passwords, and generating and storing salts. Our
    ;; database implementation satisfies cylon.user.protocols/UserStore.
    :password-verifier
    (-> (new-durable-password-verifier)
        (using {:password-hash-algo :password-hash-algo
                :user-store :user-store}))

    ;; This component provides all the Cylon callback functions for
    ;; email text and HTML responses for authorization server protocols.
    :user-form-renderer (-> (new-user-form-renderer)
                            (using {:templater :clostache-templater-templater})
                            (co-using {:router :authorization-server-webrouter}))


    ;; A login form, which manages the authentication 'interaction' with
    ;; a user, providing the outcome to other interested components.
    :login
    (-> (new-login :uri-context "/auth")
        (using {:renderer :user-form-renderer
                :user-store :user-store
                :session-store :authorization-server-session-store
                :password-verifier :password-verifier})
        (co-using {:router :authorization-server-webrouter}))

    ;; A reset-password form, which manages ....
    :reset-password
    (-> (new-reset-password :uri-context "/auth")
        (using {:user-store :user-store
                :session-store :authorization-server-session-store
                :renderer :user-form-renderer
                ;; TODO: Use a store that has a reset policy of a few hours
                :verification-code-store :verification-code-store
                :password-verifier :password-verifier
                :emailer :emailer})
        (co-using {:router :authorization-server-webrouter}))

    ;; When OAuth clients contact the authorization server, it looks them up
    ;; in its registry. Since registration of all clients is part of
    ;; this system, we don't (yet) use a durable registry.
    :oauth-client-registry (new-ref-backed-client-registry)

    ;; One of the roles of an OAuth2 authorization server is to issue
    ;; access tokens that can be used to access resources. This is
    ;; specified here.
    ;; Access tokens last 24 hours, but this is expected to be reduced
    ;; once the capability to refresh tokens has been implemented.
    :oauth-access-token-store (new-atom-backed-token-store
                         :ttl-in-secs (* 60 60 24))

    ;; Now for the authorization server.
    :authorization-server
    (-> (new-authorization-server
         :scopes { ;; These you get when you sign up
                  :user {:description "Read all your resources"} ; allows to read your devices/topics
                  :user/write-resource {:description "Create new resource, and modify and delete your existing resources "}

                  ;; These you get when you pay money
                  :user/create-feature-resource {:description "Create featured resources"}
                 }
         :iss "https://cylon-demo.com"
         :uri-context "/auth")
        (using {:session-store :authorization-server-session-store
                :authentication-handshake :login
                :client-registry :oauth-client-registry
                :access-token-store :oauth-access-token-store}))

    ;; It is desirable for users to be able to explicitly logout of
    ;; sessions.
    :logout (-> (new-logout :uri-context "/auth")
                (using
                 {:session-store :authorization-server-session-store}))

    ;; When users sign up, a verification code will be emailed to them,
    ;; which allows us to authenticate email addresses. We must store
    ;; these codes so that we know which user has been issued which
    ;; code, and to verify the authenticity of a link when the user
    ;; clicks on it from their email. Not every user will check their
    ;; email immediately, so we set a grace period of 90 days.
    :verification-code-store
    (new-atom-backed-token-store
     :ttl-in-secs (* 60 60 24 90)       ; 90 days
     )

    ;; The sign-up form, like the login-form, takes a renderer and has
    ;; dependencies on many of the components already defined.
    :signup-event-hook
    (reify EventPublisher
      (raise-event! [_ ev] (warnf "User created!" ev)))

    :signup-form
    (-> (new-signup-with-totp :uri-context "/auth" :post-signup-redirect (str (get-in config [:webapp :location]) "/index.html"))
        (using {:user-store :user-store
                :password-verifier :password-verifier
                :session-store :authorization-server-session-store
                :renderer :user-form-renderer
                :verification-code-store :verification-code-store
                :emailer :emailer
                :events :signup-event-hook})
        (co-using {:router :authorization-server-webrouter}))

    ;; A bidi-compatible router brings together components that provide
    ;; web services.
    :authorization-server-webrouter
    (-> (new-router)
        (using [:authorization-server :login :reset-password :logout :signup-form :jquery-resources :public-resources-public-resources :twitter-bootstrap-service]))

    ;; Finally, the router is made accessible over HTTP, using an
    ;; http-kit listener. The authorization server is now fully defined
    ;; and ready to start.
    :authorization-server-http-listener
    (-> (new-webserver :port (get-in config [:auth-server :port]) )
        (using {:request-handler :authorization-server-webrouter}))))


(defn add-oauth-client
  "Add a web application.

  Web applications are OAuth2 clients."
  [system config]
  (assoc system
    ;; TODO: Why doesn't web app need :web-resources? Is it duplicating
    ;; these routes?

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

         :required-scopes #{:user
                            :user/write-devices
                            :user/write-topics
                            :user/create-private-topics
                            :superuser/read-users
                            :superuser/create-topics}

         ;; Perhaps we could get these during dynamic registration with
         ;; the client-registry?
         :authorize-uri
         (str (get-in config [:auth-server :location]) "/auth/authorize")

         :access-token-uri
         (str (or (get-in config [:auth-server :local-location])
                  (get-in config [:auth-server :location])) "/auth/access-token")

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
          (add-user-store config)
          (add-emailer config)
          (add-authorization-server config)
          (add-oauth-client config)
          (http-listener-components config)
          (modular-bidi-router-components config)
          (clostache-templater-components config)
          (public-resources-components config)
          (bootstrap-cover-website-components config)
          (twitter-bootstrap-components config)
          (jquery-components config)))))

(defn new-dependency-map
  []
  {:http-listener-listener {:request-handler :modular-bidi-router-webrouter},
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
