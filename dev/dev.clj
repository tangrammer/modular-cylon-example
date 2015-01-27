(ns dev
  (:require
   [clojure.pprint :refer (pprint)]
   [clojure.reflect :refer (reflect)]
   [clojure.repl :refer (apropos dir doc find-doc pst source)]
   [clojure.tools.namespace.repl :refer (refresh refresh-all)]
   [clojure.java.io :as io]
   [com.stuartsierra.component :as component]
   [tangrammer.component.co-dependency :as co-dependency]
   [modular.cylon-oauth-example.system :refer (config new-system-map new-dependency-map new-co-dependency-map http-listener-components)]
   [modular.maker :refer (make)]
   [bidi.bidi :refer (match-route path-for)]
   [modular.wire-up :refer (normalize-dependency-map)]
   [modular.ring :refer (request-handler)]

   ))


(def system nil)

(defn new-dev-system
  "Create a development system"
  []
  (let [config (config)
        s-map (->
               (new-system-map config)
               (http-listener-components config)
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


(comment

  "correct data y response"
  curl https://www.googleapis.com/userinfo/v2/me?access_token=ya29.CAHlPdGnZg4-vdt7bdoP2_HuahmhuMPaQRYQQB0YlX2Gq5XfXH1j4B7qmJDrsKrY203ikqM3cr4C9w

{
 "id": "110671093781330642976",
 "name": "JUAN ANTONIO Ruz",
 "given_name": "JUAN ANTONIO",
 "family_name": "Ruz",
 "link": "https://plus.google.com/110671093781330642976",
 "picture": "https://lh4.googleusercontent.com/-_tX78fJlNPc/AAAAAAAAAAI/AAAAAAAAFeQ/MlFtcBlNnI0/photo.jpg",
 "gender": "male",
 "locale": "es"
 }

"with an old access-token"
curl https://www.googleapis.com/userinfo/v2/me?access_token=ya29.AQHkeukl8aQNazx0BD_Gvp5kqzK_DdVtj1sdODo9aDCe6PGOWncWAsvqj-_gScdBjVIvSYqw8qOmmQ
{
 "error": {
  "errors": [
   {
    "domain": "global",
    "reason": "authError",
    "message": "Invalid Credentials",
    "locationType": "header",
    "location": "Authorization"
   }
  ],
  "code": 401,
  "message": "Invalid incorrect"
 }
}


 "Credentials access_token it has a character less"
curl https://www.googleapis.com/userinfo/v2/me?access_token=ya29.CAHlPdGnZg4-vdt7bdoP2_HuahmhuMPaQRYQQB0YlX2Gq5XfXH1j4B7qmJDrsKrY203ikqM3cr4C9
{
 "error": {
  "errors": [
   {
    "domain": "global",
    "reason": "authError",
    "message": "Invalid Credentials",
    "locationType": "header",
    "location": "Authorization"
   }
  ],
  "code": 401,
  "message": "Invalid Credentials"
 }
}


)
