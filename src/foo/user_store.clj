(ns foo.user-store
  (:require   [com.stuartsierra.component :as component :refer (using)]
              [cylon.user.protocols :refer (UserStore)]
              [cylon.user.totp :as t :refer (OneTimePasswordStore set-totp-secret get-totp-secret)]
              [plumbing.core :refer (<-)]))

(defrecord UserTokenStore [host port dbname user password]
  component/Lifecycle
  (start [this]
    #_(assoc this
      :connection
      {:subprotocol "postgresql"
       :classname "org.postgresql.Driver"
       :subname (format "//%s:%d/%s" host port dbname)
       :user user
       :password password})
    this)
  (stop [this] this)

  UserStore
  (create-user! [component uid {:keys [hash salt]} email user-details]

    #_(j/insert!
     (:connection component)
     :users {:id uid
             :name (:name user-details)
             :email email
             :password_hash hash
             :password_salt salt
             :role "user"}))

  (get-user [component uid]
    #_(when-let [row (first (j/query (:connection component)
                                   ["SELECT * FROM users WHERE id = ?" uid]))]
      {:uid (:id row)
       :name (:name row)
       :email (:email row)}))

  (get-user-password-hash [component uid]
    #_(when-let [row (first (j/query (:connection component)
                                   ["SELECT * FROM users WHERE id = ?" uid]))]
      {:hash (:password_hash row)
       :salt (:password_salt row)}))

  (set-user-password-hash! [component uid {:keys [hash salt]}]
    #_(j/update!
     (:connection component)
     :users {:password_hash hash
             :password_salt salt}
     ["id = ?" uid]))

  (get-user-by-email [component email]
    #_(when-let [row (first (j/query (:connection component)
                                   ["SELECT * FROM users WHERE email = ?" email]))]
      {:uid (:id row)
       :name (:name row)
       :email (:email row)}))

  (delete-user! [component uid]
    #_(j/delete!
     (:connection component)
     :users ["id = ?" uid]))

  (verify-email! [component uid]
    #_(j/update!
     (:connection component)
     :users {:email_verified true} ["id = ?" uid]))


  OneTimePasswordStore
  (set-totp-secret [this identity encrypted-secret]
    #_(j/insert! (:connection this) :totp_secrets {:user_id identity :secret encrypted-secret})
    )

  (get-totp-secret [this identity]
    #_(:secret (first (j/query (:connection this) ["SELECT secret from totp_secrets WHERE user_id = ?" identity])))
   ))


(defn new-user-store
  [& {:as opts}]
  (->> opts
       map->UserTokenStore
       (<- (using [:user-token-store]))))
