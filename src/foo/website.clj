(ns foo.website
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
   [cylon.oauth.client :refer (wrap-require-authorization)]
))

(defn menu [router uri signup-item]
  (hiccup/html
   [:ul.nav.masthead-nav
    (concat (for [[k label] [[::index "Home"]
                      [::features "Features"]
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
       ) signup-item)]))

(defn page [templater router oauth-router oauth-listener req content]
  (response
   (render-template
    templater
    "templates/page.html.mustache" ; our Mustache template
    {:menu (menu router  (:uri req)
                 [[:li
                   [:a (merge {:href (format "%s://%s:%s%s"
                                             (name (:scheme req))
                                             (:server-name req)
                                             (:port @oauth-listener)
                                             (path-for (:routes @oauth-router) :cylon.user.signup/GET-signup-form))})
                    "Sign up"]]])
     :content content})))

(defn index [templater router oauth-router oauth-listener]
  (fn [req]
    (page templater router oauth-router oauth-listener req
          (hiccup/html
           [:div
            [:h1.cover-heading "Welcome"]
            [:p.lead "Cover is a one-page template for
                  building simple and beautiful home pages. Download,
                  edit the text, and add your own fullscreen background
                  photo to make it your own."]
            [:p "This is a Clojure project called foo, generated
            from modular's bootstrap-cover template. This text can be
            found in " [:code "foo/website.clj"]] ]))))

(defn features [oauth-client templater router oauth-router oauth-listener]
  (-> (fn [req]
     (page templater router oauth-router oauth-listener req
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
             [:p "This list can be found in " [:code "foo/website.clj"]]])))
      (wrap-require-authorization oauth-client :user)
      ))

(defn about [templater router oauth-router oauth-listener]
  (fn [req]
    (page templater router oauth-router oauth-listener req
          (hiccup/html
           [:div
            [:h1.cover-heading "About"]
            [:p.lead "You should
            edit " [:code "foo/website.clj"] ", locate
            the " [:code "about"] " function and edit the function
            defintion to display your details here, describing who you are
            and why you started this project."]]))))

;; Components are defined using defrecord.

(defrecord Website [oauth-client templater router oauth-router oauth-listener]

  ; modular.bidi provides a router which dispatches to routes provided
  ; by components that satisfy its WebService protocol
  WebService
  (request-handlers [this]
    ;; Return a map between some keywords and their associated Ring
    ;; handlers
    {::index (index templater router oauth-router oauth-listener)
     ::features (features oauth-client templater router oauth-router oauth-listener)
     ::about (about templater router oauth-router oauth-listener)})

  ;; Return a bidi route structure, mapping routes to keywords defined
  ;; above. This additional level of indirection means we can generate
  ;; hyperlinks from known keywords.
  (routes [_] ["/" {"index.html" ::index
                    "" (redirect ::index)
                    "features.html" ::features
                    "about.html" ::about}])

  ;; A WebService can be 'mounted' underneath a common uri context
  (uri-context [_] ""))

;; While not mandatory, it is common to use a function to construct an
;; instance of the component. This affords the opportunity to control
;; the construction with parameters, provide defaults and declare
;; dependency relationships with other components.

(defn new-website []
  (-> (map->Website {})
      (using [:templater :oauth-client])
      (co-using [:router :oauth-router :oauth-listener])))
