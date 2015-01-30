(ns modular.cylon-oauth-example.website
  (:require
   [bidi.bidi :refer (path-for)]
   [bidi.ring :refer (redirect)]
   [ring.util.response :as ring-response]
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
   [cylon.session :refer (session respond-with-new-session! assoc-session-data! respond-close-session!)]
   [modular.cylon-oauth-example.protocols :refer (EmployeeStore get-e put-e all update-e)]
   [cylon.util :refer (absolute-uri)]
   [ring.middleware.params :refer (wrap-params)]
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
                             [::calendar-home "Buscador"]]

                  :let [href (path-for (:routes @router) k)]]
              [:li (when (= href uri) {:class "active"})
               [:a (merge {:href href}) label]]
              ) oauth-item)]))

(defn menu-extended [router req oauth-client ]
  (menu router  (:uri req)
        (when (:cylon/access-token (authenticate oauth-client req))
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

(defn index [employees-store templater router oauth-client ]
  (fn [req]

    (let
        [menu (menu-extended router req oauth-client )]


      (page templater menu
           (hiccup/html
            [:div
             [:h1.cover-heading (str "Bienvenid@!")]

             [:p.lead "Esta aplicación es de la asociación -- y sirve para buscar los asociados que estén disponibles en una fecha determinada"]
             [:p.lead "Haz click en " [:a {:href (path-for (:routes @router) ::calendar-home)} "'Buscador'"] " para empezar"]
             [:p "requisitos: Tienes que pertenecer a la asociación y tener una cuenta google y google calendar con tus eventos"]
             ])))))


(defn calendars [access-token]
  (let [r (cylon.oauth.client/http-request-form :get "https://www.googleapis.com/calendar/v3/users/me/calendarList"

                                                nil
                                                {"Authorization" (str "Bearer " access-token)})]

    (if-not (get-in r [:body "error"])
      (->> (get (:body r) "items")
          (map #(vector
                (get % "summary")
                (get % "id")) )
          )
      (println (get-in r [:body "error"])))))



(defn cal-events [access-token id]
  (let [r (cylon.oauth.client/http-request-form :get (format "https://www.googleapis.com/calendar/v3/calendars/%s/events" id)

                                                nil
                                                {"Authorization" (str "Bearer " access-token)})]

    (if-not (get-in r [:body "error"])
      (->> (get (:body r) "items")
          (map #(vector
                (get % "start")
                (get % "end")) )
          )
      (println (get-in r [:body "error"])))))




(defn me [access-token]
  (let [r (cylon.oauth.client/http-request-form :get "https://www.googleapis.com/userinfo/v2/me"
                                                nil
                                                {"Authorization" (str "Bearer " access-token)})]

      (if-not (get-in r [:body "error"])
      {:email (get (:body r) "email")
       :name (get (:body r) "name")}
      (println (get-in r [:body "error"])))))

(defn select-calendar [employees-allowed employees-store templater router oauth-client session-store ]
  (-> (fn [req]
        (let [cal (get (:query-params req) "cal")
              email (:cylon/email (session session-store req))
              refresh-token (:cylon/refresh-token req)
              redirect-uri (:buscador/redirect-uri (session session-store req))
              ]



          (do
            (println "store cal" cal)
            (update-e employees-store email {:calendar cal})
            (assoc-session-data! session-store req {:buscador/calendar cal})
            ;; select calendar
            (ring-response/redirect redirect-uri)
            #_(page templater (menu-extended router req oauth-client)
                  (hiccup/html
                   [:div
                    [:h1.cover-heading "You've selected: " cal]])))))
      wrap-params
      (wrap-require-authorization oauth-client  "https://www.googleapis.com/auth/calendar.readonly")))


;; *calendar_home* call!
;; checks if has identity
;; checks if has priviledges to show me calendar and email
;; checks if email exists in allowed-employees
;; if first time: ....
;;              stores the refresh-token (with email )
;;              show calendar names to select one
;;              redirect_to *select_calendar*

;; else
;;    store :weapp-session loaded_access_tokens_employees
;;    show:...
;;TODO:       employees (activated) table:  (those who have refres-token so we can access their identity and calendar events)
;;TODO:       search form: with calendar component input and morning/afternoon select input
;;TODO:                    action *search_employees*
;;

;; *select_calendar* call!
;; show select with all calendars

;; on-select >
;;            stores the calendar name (with email and refresh-token)
;;            redirect_to *calendar_home*


;;TODO: *search_employees* call!
;;TODO:  show: search_results (perhaps and search_form)
;;


(defn calendar-home [employees-allowed employees-store templater router oauth-client session-store ]
  (-> (fn [req]
        (let [access-token (:cylon/access-token req)
              email (or (:cylon/email (session session-store req))
                        (do
                          (when-let [e (:email (me access-token))]
                            (assoc-session-data! session-store req {:cylon/email e})
                            e)))
              refresh-token (:cylon/refresh-token req)]

          ;; only in first call with oauth2 server we get refresh-token!!
          (when (and refresh-token (contains? employees-allowed email))
            (do
              (println "refresh-token" refresh-token)
              (put-e employees-store {:id email
                                      :token access-token
                                      :refresh-token refresh-token})

              ;; select calendar


              )
            )
          (if-let [calendar (or (:calendar (get-e employees-store email)) (:buscador/calendar (session session-store req)) )]

            (page templater (menu-extended router req oauth-client)
                (hiccup/html
                 [:div
                  [:h1.cover-heading "Protected Info" email]
                  [:p (format "access-token: %s" (:cylon/access-token req))]
                  [:p (format "calendar: %s" calendar)]
                  [:h2 "first search form!"]
                  [:p "testing: anyway here first 10 events for your calendar"]
                  [:ul
                   (for [[start end] (take 10 (cal-events access-token calendar))]
                     [:li (str (get start "dateTime") " - " (get end "dateTime"))]
                     )]
                  ]))
            (page templater (menu-extended router req oauth-client)
                  (do
                    (assoc-session-data! session-store req {:buscador/redirect-uri (absolute-uri req)})
                    (hiccup/html
                     [:div
                      [:h1.cover-heading "Select your  calendar " email]
                      [:div.dropdown {:style "color:black;"}
                       [:button {:id "dLabel" :type "button" :data-toggle "dropdown" :aria-haspopup "true" :aria-expanded "false"}
                        "Selecciona tu calendario" [:span.caret]]
                       [:ul.dropdown-menu {:role "menu" :aria-labelledby "dLabel"}
                        (for [[n id] (calendars access-token)]
                          [:li.dropdown [:a {:href (str  (path-for (:routes @router) ::select-calendar) "?cal=" id) } n]])
]]
                      [:p refresh-token]])))

            )
          ))
      (wrap-require-authorization oauth-client  "https://www.googleapis.com/auth/calendar.readonly")))

(defrecord Website [oauth-client templater router  employees-store employees-allowed session-store]
  WebService
  (request-handlers [this]
    {::index (index employees-store templater router oauth-client)
     ::calendar-home (calendar-home employees-allowed employees-store templater router oauth-client session-store)
     ::select-calendar (select-calendar employees-allowed employees-store templater router oauth-client session-store)
     }
    )

  (routes [_] ["/" {"index.html" ::index
                    "" (redirect ::index)
                    "buscador.html" ::calendar-home
                    "select-calendar.html" ::select-calendar
                    }])
  (uri-context [_] ""))

(defn new-website [& {:as opts}]
  (-> (map->Website opts)
      (using [:templater :oauth-client :employees-store :session-store])
      (co-using [:router])))
