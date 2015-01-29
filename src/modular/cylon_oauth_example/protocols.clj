(ns modular.cylon-oauth-example.protocols)




(defprotocol EmployeeStore
  (put-e [_ options])
  (get-e [_ id])
  (all [_])
  )
