(ns modular.cylon-oauth-example.user-store
  (:require   [com.stuartsierra.component :as component :refer (using)]
              [cylon.user.protocols :refer (UserStore get-user)]
              [cylon.token-store.protocols :refer (TokenStore create-token! get-token-by-id renew-token! purge-token! dissoc-token! merge-token!)]
              [cylon.user.totp :as t :refer (OneTimePasswordStore set-totp-secret get-totp-secret)]
              [plumbing.core :refer (<-)])
  (:import [com.google.appengine.api.datastore Entity DatastoreService DatastoreServiceFactory] )
  )


(defrecord MyUserStore [host port dbname user password token-store]
  UserStore
  (create-user! [this uid {:keys [hash salt]} email user-details]
    (create-token! token-store uid {:id uid
                                    :name (:name user-details)
                                    :email email
                                    :password_hash hash
                                    :password_salt salt
                                    :role "user"}))

  (get-user [this uid]
    (when-let [row (get-token-by-id token-store uid)]
      {:uid (:id row)
       :name (:name row)
       :email (:email row)}))

  (get-user-password-hash [this uid]
    (when-let [row (get-token-by-id token-store uid)]
      {:hash (:password_hash row)
       :salt (:password_salt row)}))

  (set-user-password-hash! [this uid {:keys [hash salt]}]
    (merge-token! token-store uid
                  {:password_hash hash
                   :password_salt salt}))

  (get-user-by-email [this email]

    (when-let [row (->>
      (-> token-store :tokens deref vals)
      (filter #(= email (:email %)))
      (first))]
      {:uid (:id row)
       :name (:name row)
       :email (:email row)}))

  (delete-user! [this uid]
    (purge-token! token-store uid))

  (verify-email! [this uid]
    (merge-token! token-store uid {:email_verified true}))


  OneTimePasswordStore
  (set-totp-secret [this identity encrypted-secret]
    )

  (get-totp-secret [this identity]
   ))


(defn new-user-store
  [& {:as opts}]
  (->> opts
       map->MyUserStore
       (<- (using [:token-store]))))
