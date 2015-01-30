(ns modular.cylon-oauth-example.employees-mock-store
  (:require   [com.stuartsierra.component :as component :refer (using)]
              [plumbing.core :refer (<-)]
              [modular.cylon-oauth-example.protocols :refer (EmployeeStore get-e put-e update-e all)])
  (:import [com.google.appengine.api.datastore Entity DatastoreService DatastoreServiceFactory Key EntityNotFoundException Query]
           [com.google.appengine.tools.development.testing LocalDatastoreServiceTestConfig LocalServiceTestHelper LocalServiceTestConfig])
  )

(defn update-prop [e k m]
  (when (k m)
        (.setProperty e (name k) (k m))))

(defn helper []
  (LocalServiceTestHelper. (into-array LocalServiceTestConfig [(LocalDatastoreServiceTestConfig.)])))

(defn datastore []
  (DatastoreServiceFactory/getDatastoreService))

(defn format-e [employee]
  {:token (.getProperty employee "token")
   :refresh-token (.getProperty employee "refresh-token")
   :id (.getName (.getKey employee))
   :calendar (.getProperty employee "calendar")
   :class "Employee"})

(defrecord GoogleEmployeeStoreMock []
  component/Lifecycle
  (start [this]
    (println "starting datastore mock")

    (.setUp (helper))
    this

    )
  (stop [this]
    (println "stoping datastore mock")
    (try

      (.tearDown (helper))
      (catch Exception e (str "exception caught "(.getMessage e))))
    this)

  EmployeeStore
  (put-e [this options]
    (try
      (Entity. "Employee" (:id options))
      (catch Exception ex  (.setUp (helper)))
      )
    (let [^DatastoreService datastore (datastore)
          ^Entity e (Entity. "Employee" (:id options))]
      (doall (map #(update-prop e % options) [:token :refresh-token :calendar]))

      (.put datastore e)
      (println "inserted  done!!" e)
      (format-e e)
      )

    )



  (get-e [this id]
    (try
      (Entity. "Employee" (:id "ii"))
      (catch Exception e  (.setUp (helper)))
      )
    (let [^DatastoreService datastore (datastore)
          ^Entity e (Entity. "Employee" id)]

                                        ;  (println "por fin!!"(.get datastore (.getKey e)))
      (try
        (let [employee (.get datastore (.getKey e))]
          (println employee)
          (format-e employee)

          )
        (catch EntityNotFoundException e (do (println (str "caught exception " (.getMessage e)))
                                             nil)))

      )

    )

  (update-e  [this id options]
    (put-e this (merge (get-e this id) options))
    )

  (all [this]

    (map format-e (iterator-seq (.asIterator (.prepare (datastore) (Query."Employee")))))
    )
  )



(defn new-employees-store
  [& {:as opts}]
  (->> opts

       map->GoogleEmployeeStoreMock))


#_(do (.setUp (helper))
    (let [store (new-employees-store)]
      (doall (repeatedly 50 #(put-e store {:id (str  "juan-" (rand-int 1000)) :token "a" :refresh-token "b"})))
;      (format-e (put-e store {:id "juan" :token "a" :refresh-token "b"}))

      (all store)
      )
    )


(do (.setUp (helper))
    (let [store (new-employees-store)]
      (put-e store {:id "juan-"  :token "a" :refresh-token "b"})
      (update-e store "juan-" {:calendar 1} )
      (get-e store "juan-")
      ))
