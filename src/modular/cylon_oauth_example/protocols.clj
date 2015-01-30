(ns modular.cylon-oauth-example.protocols)




(defprotocol EmployeeStore
  (put-e [_ options])
  (get-e [_ id])
  (update-e  [_ id options])
  (all [_])
  )
