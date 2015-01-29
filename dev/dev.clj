(ns dev
  (:require
   [clojure.pprint :refer (pprint)]
   [clojure.reflect :refer (reflect)]
   [clojure.repl :refer (apropos dir doc find-doc pst source)]
   [clojure.tools.namespace.repl :refer (refresh refresh-all)]
   [clojure.java.io :as io]
   [com.stuartsierra.component :as component]
   [tangrammer.component.co-dependency :as co-dependency]
   [modular.cylon-oauth-example.system :refer (config new-system-map new-dependency-map new-co-dependency-map)]
   [modular.maker :refer (make)]
   [bidi.bidi :refer (match-route path-for)]
   [modular.wire-up :refer (normalize-dependency-map)]
   [modular.ring :refer (request-handler)]
   [modular.http-kit :refer (new-webserver)]
   [modular.cylon-oauth-example.employees-mock-store :refer (new-employees-store)]
   )
  )


(defn http-listener-components [system config]
  (assoc system
    :http-listener-listener
    (->
     (make new-webserver config :port (get-in config [:webapp :port]))
     (component/using {:request-handler :modular-bidi-router-webrouter})
     (co-dependency/co-using []))))

(defn google-datastore-mock-component [system config]
  (assoc system
    :employees-store
    (new-employees-store)))

(def system nil)

(defn new-dev-system
  "Create a development system"
  []
  (let [config (config)
        s-map (->
               (new-system-map config)
               (http-listener-components config)
               (google-datastore-mock-component config)
               #_(assoc
                     ))]
    (-> s-map
        (component/system-using (new-dependency-map))
        (co-dependency/system-co-using (new-co-dependency-map))
        )))

(defn init
  "Constructs the current development system."
  []
  (alter-var-root #'system
    (constantly (new-dev-system))))

(defn start
  "Starts the current development system."
  []
  (alter-var-root
   #'system
   co-dependency/start-system
   ))



(defn stop
  "Shuts down and destroys the current development system."
  []
  (alter-var-root #'system
                  (fn [s] (when s (component/stop s)))))

(defn go
  "Initializes the current development system and starts it running."
  []
  (init)
  (start)
  :ok
  )
(declare insert-user)
(defn reset []
  (stop)
  (refresh :after 'dev/go)

  )

;; REPL Convenience helpers

(defn routes []
  (-> system :modular-bidi-router-webrouter :routes))

(defn path->route [path]
  (match-route (routes) path))

(defn insert-user [uid pass name email]
  (let [pw-hash (cylon.password/make-password-hash (-> system :password-verifier) pass)]
    (cylon.user/create-user! (-> system :user-store) uid pw-hash email {:name name})

    ))

(defn reset+data []
  ;; reset && insert test user
  (when (= :ok (reset))
    (insert-user "tangrammer" "clojure" "Juan" "juanantonioruz@gmail.com")
    (pprint (-> system :user-store))
    :reset+data-ok))

(defn get-refresh-token []
  (-> system :webapp-session-store :token-store :tokens  deref first second :cylon/refresh-token ))

(defn refresh-token []
 (apply cylon.oauth.client/refresh-token*
        (-> ((juxt :access-token-uri :client-id :client-secret) (-> system :webapp-oauth-client))
            (conj (get-refresh-token)))))


(defn me [access-token]
  (let [r (cylon.oauth.client/http-request-form :get "https://www.googleapis.com/userinfo/v2/me"
                                                nil
                                                {"Authorization" (str "Bearer " access-token)})]

      (if-not (get-in r [:body "error"])
      {:email (get (:body r) "email")
       :name (get (:body r) "name")}
      (println (get-in r [:body "error"])))))
