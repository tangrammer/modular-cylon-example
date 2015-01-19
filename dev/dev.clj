(ns dev
  (:require
   [rhizome :as r]
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


   [milesian.bigbang :as bigbang]
   [milesian.identity :as identity]
   [milesian.aop :as aop]
   [milesian.aop.utils  :refer (extract-data)]
   [milesian.system-diagrams :refer (store-message try-to-publish store)]
   ))


(def system nil)

(defn diagram
  "to get sequence diagram we need the ->start-fn-call and
  the <-return-fn-call times of the fn invocation call.
  The sequence will be published if all fns are finished (:closed)"
  [*fn* this & args]
  (let [invocation-data (extract-data *fn* this args)]
    (store-message invocation-data :opened)
    (let [res (apply *fn* (conj args this))]
      (store-message invocation-data :closed)
      (try-to-publish  #'dev/system)
      res)))

(defn new-dev-system
  "Create a development system"
  []
  (let [config (config)
        s-map (->
               (new-system-map config)
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

#_(defn start
  "Starts the current development system."
  []
  (alter-var-root
   #'system
   co-dependency/start-system
   ))

(defn start
  "Starts the current development system."
  []
  (let [future-system (atom system)]
    (alter-var-root #'system #(bigbang/expand % {:before-start [[identity/add-meta-key %]
                                                                [identity/assoc-meta-who-to-deps]
                                                                [co-dependency/assoc-co-dependencies future-system]]
                                                 :after-start [[aop/wrap diagram ]
                                                              [co-dependency/update-atom-system future-system]]}))))

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
  (reset)
  (insert-user "tangrammer" "clojure" "Juan" "juanantonioruz@gmail.com")
  :reset+data-ok)




(comment
  "this lines to generate with rhizome the images of http://tangrammer.github.io/posts/13-01-2015-using-cylon-oauth2.html"
  ;; TODO: remove after publishing full doc
  :http-listener-listener :authorization-server-http-listener


:webapp-token-store :authorization-server-token-store :user-token-store :oauth-access-token-store
:password-hash-algo


  (->> (set [:jquery-resources :twitter-bootstrap-service :bootstrap-cover-website-website :public-resources-public-resources :clostache-templater-templater :modular-bidi-router-webrouter :http-listener-listener])
      (r/system-graph system)
      (r/save-system-image #{} #{}))


  (->> (disj (set (keys system)) :http-listener-listener :authorization-server-http-listener :webapp-token-store :authorization-server-token-store :user-token-store
)
      (r/system-graph system)
      (r/save-system-image #{:twitter-bootstrap-service  :jquery-resources :public-resources-public-resources } #{:clostache-templater-templater}))
 )
