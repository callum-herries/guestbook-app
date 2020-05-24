(ns app.core
  (:require
   [day8.re-frame.http-fx]
   [reagent.dom :as rdom]
   [reagent.core :as r]
   [re-frame.core :as rf]
   [goog.events :as events]
   [goog.history.EventType :as HistoryEventType]
   [markdown.core :refer [md->html]]
   [app.ajax :as ajax]
   [ajax.core :refer [GET POST]]
   [app.events]
   [reitit.core :as reitit]
   [reitit.frontend.easy :as rfe]
   [clojure.string :as string]
   [app.validation :as validation])
  (:import goog.History))

(defn nav-link [uri title page]
  [:a.navbar-item
   {:href   uri
    :class (when (= page @(rf/subscribe [:common/page])) :is-active)}
   title])

(defn navbar []
  (r/with-let [expanded? (r/atom false)]
    [:nav.navbar.is-info>div.container
     [:div.navbar-brand
      [:a.navbar-item {:href "/" :style {:font-weight :bold}} "app"]
      [:span.navbar-burger.burger
       {:data-target :nav-menu
        :on-click    #(swap! expanded? not)
        :class       (when @expanded? :is-active)}
       [:span][:span][:span]]]
     [:div#nav-menu.navbar-menu
      {:class (when @expanded? :is-active)}
      [:div.navbar-start
       [nav-link "#/" "Home" :home]
       [nav-link "#/about" "About" :about]]]]))

(defn about-page []
  [:section.section>div.container>div.content
   [:img {:src "/img/warning_clojure.png"}]])

(defn message-list [messages]
  [:ul.messages
   (for [{:keys [timestamp name message]} @messages]
     ^{:key timestamp}
     [:li
      [:time (.toLocaleString timestamp)]
      [:p message]
      [:p "@" name]])])

(defn send-message! [fields errors]
  (if-let [validation-errors (validation/validate-message @fields)]
    (reset! errors validation-errors)
    (POST "/api/message"
          {:format  :json
           :headers {"Accept" "application/transit+json"}
           :params  @fields
           :handler #(do
                       (rf/dispatch [:messages/add (-> @fields
                                                       (assoc :timestamp (js/Date.))
                                                       (update :name str " [Client]"))])
                       (reset! fields nil)
                       (reset! errors nil))})))

(defn errors-component [id]
  (when-let [error @(rf/subscribe [:form/error id])]
    [:div.notification.is-danger (string/join error)]))

(defn message-form []
  [:div
   [errors-component :server-error]
   [:div.field
    [:label.label {:for :name} "Name"]
    [errors-component :name]
    [:input.input
     {:type      :text
      :name      :name
      :on-change #(rf/dispatch [:form/set-field :name (.. % -target -value)])
      :value     @(rf/subscribe [:form/field :name])}]]
   [:div.field
    [:label.label {:for :message} "Message"]
    [errors-component :message]
    [:textarea.textarea
     {:name      :message
      :on-change #(rf/dispatch [:form/set-field :message (.. % -target -value)])
      :value     @(rf/subscribe [:form/field :message])}]]
   [:input.button.is-primary
    {:type     :submit
     :disabled @(rf/subscribe [:form/validation-errors?])
     :on-click #(rf/dispatch [:message/send! @(rf/subscribe [:form/fields])])
     :value    "comment"}]])

(defn home-page []
  (let [messages (rf/subscribe [:messages/list])]
    [:section.section>div.container>div.content
     [:div.columns
      [:div.column
       [message-list messages]
       [:h1 "Messages"]
       [message-form]]]]))

(defn page []
  (if-let [page @(rf/subscribe [:common/page])]
    [:div
     [navbar]
     [page]]))

(defn navigate! [match _]
  (rf/dispatch [:common/navigate match]))

(def router
  (reitit/router
    [["/" {:name        :home
           :view        #'home-page
           :controllers [{:start (fn [_] (rf/dispatch [:page/init-home]))}]}]
     ["/about" {:name :about
                :view #'about-page}]]))

(defn start-router! []
  (rfe/start!
    router
    navigate!
    {}))

;; -------------------------
;; Initialize app
(defn ^:dev/after-load mount-components []
  (.log js/console "Mounting Components...")
  (rf/clear-subscription-cache!)
  (rdom/render [#'page] (.getElementById js/document "app"))
  (.log js/console "Components Mounted!"))

(defn get-messages! []
  (GET "/api/messages"
       {:headers {"Accept" "application/transit+json"}
        :handler #(rf/dispatch [:messages/set (:messages %)])}))

(defn init! []
  (start-router!)
  (get-messages!)
  (ajax/load-interceptors!)
  (mount-components))
