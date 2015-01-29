(ns modular.cylon-oauth-example.website
  (:require
   [bidi.bidi :refer (path-for)]
   [bidi.ring :refer (redirect)]
   [clojure.pprint :refer (pprint)]
   [clojure.tools.logging :refer :all]
   [com.stuartsierra.component :refer (using)]
   [hiccup.core :as hiccup]
   [modular.bidi :refer (WebService as-request-handler)]
   [modular.ring :refer (WebRequestHandler)]
   [modular.template :refer (render-template template-model)]
   [ring.util.response :refer (response)]
   [tangrammer.component.co-dependency :refer (co-using)]
   [cylon.oauth.client :refer (wrap-require-authorization refresh-token*)]
   [cylon.authentication :refer (authenticate)]
   [modular.cylon-oauth-example.protocols :refer (get-e put-e all)]

   )
     (:import [com.google.appengine.api.datastore Entity DatastoreService DatastoreServiceFactory EntityNotFoundException] ))

(defn make-full-link [req port-listener router path]
  (format "%s://%s:%s%s"
          (name (:scheme req))
          (:server-name req)
          port-listener
          (path-for (:routes router) path)))

(defn menu [router uri oauth-item]
  (hiccup/html
   [:ul.nav.masthead-nav
    (concat (for [[k label] [[::index "Home"]
                             [::features "Features"]
                             [::protected "Protected"]
                             [::about "About"]]
                  ;; This demonstrates the generation of hyperlinks from
                  ;; keywords.

                  ;; by the way, router is deref'd because it's a
                  ;; co-dependency, this is likely to change to potemkin's
                  ;; def-map-type in future releases, so a deref will be
                  ;; unnecessary (and deprecated)

                  :let [href (path-for (:routes @router) k)]]
              [:li (when (= href uri) {:class "active"})
               [:a (merge {:href href}) label]]
              ) oauth-item)]))

(defn menu-extended [router req oauth-client signup-uri]
  (menu router  (:uri req)
                 (if-not (:cylon/subject-identifier (authenticate oauth-client req))
                   [[:li
                      [:a (merge {:href (path-for (:routes @router) ::protected)})
                       "Log In"]]
                     [:li
                      [:a (merge {:href signup-uri})
                       "Sign Up"]]]
                   [[:li
                     [:a (merge {:href (str (path-for (:routes @router) :cylon.oauth.client.web-client/logout)
                                            "?post_logout_redirect_uri="
                                            (make-full-link req (:server-port req) @router ::index))})
                      "Log Out"]]])))

(defn page [templater menu content]
  (response
   (render-template
    templater
    "templates/page.html.mustache"
    {:menu menu
     :content content})))

(defn index [employees-store templater router oauth-client signup-uri]
  (fn [req]

    (let
        [menu (menu-extended router req oauth-client signup-uri)]


      (page templater menu
           (hiccup/html
            [:div
             [:h1.cover-heading (str "Welcome - " "juanantonioruz@gmail.com - " (get-e employees-store "juanantonioruz@gmail.com") )]
             [:p.lead "Cover is a one-page template for
                  building simple and beautiful home pages. Download,
                  edit the text, and add your own fullscreen background
                  photo to make it your own."]
             [:p "This is a Clojure project called cylon-oauth-example, generated
            from modular's bootstrap-cover template. This text can be
            found in " [:code "modular.cylon-oauth-example/website.clj"]] ])))))

(defn features [employees-store templater router oauth-client signup-uri]
  (fn [req]
    (let [menu (menu-extended router req oauth-client signup-uri)]
      (page templater menu
            (hiccup/html
             [:div
              [:h1.cover-heading "Features"]
              [:p.lead "bootstrap-cover exhibits the following :-"]
              [:ul.lead
               [:li "A working Clojure-powered website using Stuart Sierra's 'reloaded' workflow and component library"]
               [:li "A fully-commented route-contributing website component"]
               [:li [:a {:href "https://github.com/juxt/bidi"} "Bidi"] " routing"]
               [:li "Co-dependencies"]
               [:li "Deployable with lein run"]
               ]
              [:p "This list can be found in " [:code "modular.cylon-oauth-example/website.clj"]]])))))

(defn me [access-token]
  (let [r (cylon.oauth.client/http-request-form :get "https://www.googleapis.com/userinfo/v2/me"
                                                nil
                                                {"Authorization" (str "Bearer " access-token)})]

      (if-not (get-in r [:body "error"])
      {:email (get (:body r) "email")
       :name (get (:body r) "name")}
      (println (get-in r [:body "error"])))))

(defn protected [employees-allowed employees-store templater router oauth-client  signup-uri]
  (-> (fn [req]
        (let [authentication (authenticate oauth-client req)
              access-token (:cylon/access-token authentication)
              refresh-token (:cylon/refresh-token authentication)]

          (when (and refresh-token (contains? employees-allowed (:email (me access-token))))
            (println "refresh-token" refresh-token)
            (put-e employees-store {:id "juanantonioruz@gmail.com"
                                    :token access-token :refresh-token refresh-token}))
          )


        (let [menu (menu-extended router req oauth-client signup-uri)]
          (page templater menu
            (hiccup/html
             [:div
              [:h1.cover-heading "Protected Info" (str (get-e employees-store "juanantonioruz@gmail.com"))]
              [:p (format "access-token: %s" (:cylon/access-token req))]
              [:p.lead "This page is only available when you are logged :-"]
              [:ul.lead
               [:li "your personal info ..."]
               [:li "your personal info ..."]]]))))
      (wrap-require-authorization oauth-client  "https://www.googleapis.com/auth/calendar.readonly")))

(defn about [templater router oauth-client signup-uri]
  (fn [req]
    (let [menu (menu-extended router req oauth-client  signup-uri)]
      (page templater menu
           (hiccup/html
            [:div
             [:h1.cover-heading "About"]
             [:p.lead "You should
            edit " [:code "modular.cylon-oauth-example/website.clj"] ", locate
            the " [:code "about"] " function and edit the function
            defintion to display your details here, describing who you are
            and why you started this project."]])))))

;; Components are defined using defrecord.

(defrecord Website [oauth-client templater router signup-uri employees-store employees-allowed]


  ; modular.bidi provides a router which dispatches to routes provided
  ; by components that satisfy its WebService protocol
  WebService
  (request-handlers [this]
    ;; Return a map between some keywords and their associated Ring
    ;; handlers
    {::index (index employees-store templater router oauth-client signup-uri)
                     ::features (features employees-store templater router oauth-client signup-uri)
                     ::protected (protected employees-allowed employees-store templater router oauth-client signup-uri)
                     ::about (about templater router oauth-client signup-uri)})

  ;; Return a bidi route structure, mapping routes to keywords defined
  ;; above. This additional level of indirection means we can generate
  ;; hyperlinks from known keywords.
  (routes [_] ["/" {"index.html" ::index
                    "" (redirect ::index)
                    "features.html" ::features
                    "protected.html" ::protected
                    "about.html" ::about}])

  ;; A WebService can be 'mounted' underneath a common uri context
  (uri-context [_] ""))

;; While not mandatory, it is common to use a function to construct an
;; instance of the component. This affords the opportunity to control
;; the construction with parameters, provide defaults and declare
;; dependency relationships with other components.

(defn new-website [& {:as opts}]
  (-> (map->Website opts)
      (using [:templater :oauth-client :employees-store])
      (co-using [:router])))
