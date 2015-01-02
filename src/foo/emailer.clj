(ns foo.emailer
  (:require
   [postal.core :refer (send-message)]
   [com.stuartsierra.component :as component]
   [cylon.user.protocols :refer (Emailer send-email!)]
   [clojure.tools.logging :refer :all]))



(defrecord MyEmailer [from settings]
  Emailer
  (send-email! [component data]
    (send-message settings
                {:from from
                 :to "juanantonioruz@gmail.com"
                 :subject (:subject data)
                 :body (:body data)})))

(defn new-emailer
  [& {:as opts}]
  (->> opts
       (merge {:from "info@modularity.org"})
       map->MyEmailer))
