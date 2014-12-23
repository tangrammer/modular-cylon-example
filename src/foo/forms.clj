(ns foo.forms
  (:require
   [com.stuartsierra.component :as component :refer (Lifecycle using)]
   [bidi.bidi :refer (path-for)]
   [schema.core :as s]
   [cylon.user.protocols :refer (LoginFormRenderer UserFormRenderer ErrorRenderer)]
   [clojure.walk :refer (postwalk)]
   [clostache.parser :refer (render-resource)]
   [clojure.tools.logging :refer :all]
   [clojure.java.io :refer (resource)]
   [plumbing.core :refer (<-)]
   [modular.template :refer (render-template template-model)]
   [tangrammer.component.co-dependency :refer (co-using)]))

(defprotocol TemplateDataValue
  (as-template-data-value [_]
    "Turn Clojure things into strings (useful for a Mustache template model)"))

(extend-protocol TemplateDataValue
  nil
  (as-template-data-value [_] "")
  clojure.lang.Keyword
  (as-template-data-value [k] (name k))
  Object
  (as-template-data-value [s] s ))

(defn stringify-map-values [a]
  (if (and (vector? a) (= (count a) 2))
    [(first a) (as-template-data-value (second a))]
    a))

(defn- model->template-model
  "Some pre-processing on the model provided by Cylon"
  [model]
  (postwalk stringify-map-values model))

(defrecord MyUserFormRenderer [templater router]
  Lifecycle
  (start [component]
    #_(assoc component
      ;; We are slurping in the template snippets here, for performance
      ;; reasons, so that they are not slurped in on every request. We
      ;; will get these results each time via (:partials
      ;; component). This is the only reason we are satisfying the
      ;; Lifecycle protocol.
      :partials {:header (slurp (resource "templates/header.html.mustache"))
                 :footer (slurp (resource "templates/footer.html.mustache"))})
    component
    )
  (stop [component] component)

  LoginFormRenderer
  (render-login-form [this req model]
    (render-template templater
                     "templates/page.html.mustache"
                     {:content
                      (let [template-model (model->template-model model)]
                        (render-resource
                         "templates/login.html.mustache"
                         (merge template-model
                                {:reset-password-link (path-for (:routes @router) :cylon.user.reset-password/request-reset-password-form)})
                         (:partials this)))}))

  UserFormRenderer
  (render-signup-form [this req model]
    (render-template templater
                     "templates/page.html.mustache"
                     {:content
                      (let [template-model (model->template-model model)]
                        (render-resource
                         "templates/signup.html.mustache"
                         (merge template-model
                                {:reset-password-link (path-for (:routes @router) :cylon.user.reset-password/request-reset-password-form)})
                         (:partials this)))})
)

  (render-welcome [_ req model]
    "Return the HTML that will be used to welcome the new user."
    (throw (ex-info "TODO" model)))

  (render-welcome-email-message [this model]
     {:subject "Welcome to opensensors.io"
      :body (format "Thanks for signing up. Please click on this link to verify your email address: %s" (:email-verification-link model))})

  (render-email-verified [component req model]
    #_(render-resource
     "templates/email-verified.html.mustache"
     (model->template-model model)
     (:partials component)))

  (render-reset-password-request-form [component req model]
    #_(render-resource
     "templates/reset-password-request.html.mustache"
     (model->template-model model)
     (:partials component)))

  (render-reset-password-email-message [_ model]
    #_{:subject "Reset password confirmation step"
     :body (format "Please click on this link to reset your password account: %s" (:link model))})

  (render-reset-password-link-sent-response [component req model]
    #_(render-resource
     "templates/reset-password-link-sent.html.mustache"
     (model->template-model model)
     (:partials component)))

  (render-password-reset-form [component req model]
    #_(render-resource
     "templates/reset-password.html.mustache"
     (model->template-model model)
     (:partials component)))

  (render-password-changed-response [component req model]
    #_(render-resource
     "templates/password-changed.html.mustache"
     (model->template-model model)
     (:partials component)))

  ErrorRenderer
  (render-error-response [component req model]
    #_(render-resource
     "templates/error-page.html.mustache"
     (merge (model->template-model model)
            {:message
             (case (:error-type model)
               :user-already-exists (format "User '%s' already exists" (:user-id model))
               "Unknown error")})

     (:partials component))))

(defn new-user-form-renderer [& {:as opts}]
  (->> opts
       (merge {})
       map->MyUserFormRenderer
       (<- (using [:templater]))
       (<- (co-using [:router]))))
