(ns modular.cylon-oauth-example.employees-store
  (:require   [com.stuartsierra.component :as component :refer (using)]
              [plumbing.core :refer (<-)]
              [modular.cylon-oauth-example.protocols :refer (EmployeeStore get-e put-e)])
  (:import [com.google.appengine.api.datastore Entity DatastoreService DatastoreServiceFactory Key EntityNotFoundException])
  )

(defn datastore []
  (DatastoreServiceFactory/getDatastoreService))
(defrecord GoogleEmployeeStore []

  EmployeeStore
  (put-e [this options]
    (let [^DatastoreService datastore (datastore)
          ^Entity e (Entity. "Employee" (:id options))]
      (.setProperty e "token" (:token options))
      (.setProperty e "refresh-token" (:refresh-token options))
      (.put datastore e)
      (println "inserted juan done!!" e)
      )

    )
  (get-e [this id]
    (let [^DatastoreService datastore (datastore)]
      (try
        (let [employee (.get datastore (.getKey (Entity. "Employee" id)))]
          {:token (.getProperty employee "token")
           :refresh-token (.getProperty employee "refresh-token")
           :id id
           :class "Employee"}
          )
        (catch EntityNotFoundException ex (do (println (str "caught exception " (.getMessage ex)))
                                             nil)))

      )

    )
  )




(defn new-employees-store
  [& {:as opts}]
  (->> opts

       map->GoogleEmployeeStore))
