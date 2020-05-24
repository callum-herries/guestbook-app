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
   [app.websockets :as ws]
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

(defn reload-messages-button []
  (let [loading? (rf/subscribe [:messages/loading?])]
    [:button.button.is-info.is-fullwidth
     {:on-click #(rf/dispatch [:messages/load])
      :disabled @loading?}
     (if @loading?
       "Loading messages"
       "Refresh messages")]))

(defn home []
  (let [messages (rf/subscribe [:messages/list])]
    (fn []
      [:div.content>div.columns.is-centered>div.column.is-two-thirds
       (if @(rf/subscribe [:messages/loading?])
         [:h3 "Loading messages"]
         [:div
          [:div.columns>div.column
           [:h1 "Messages"]
           [message-list messages]]
          [:div.columns>div.column
           [reload-messages-button]]
          [:div.columns>div.column
           [message-form]]])])))

(defn handle-response! [response]
  (if-let [errors (:errors response)]
    (rf/dispatch [:form/set-server-errors errors])
    (do
      (rf/dispatch [:messages/add response])
      (rf/dispatch [:form/clear-fields response]))))

;; -------------------------
;; Initialize app
(defn ^:dev/after-load mount-components []
  (.log js/console "Mounting Components...")
  (rf/clear-subscription-cache!)
  (rdom/render [#'home] (.getElementById js/document "app"))
  (.log js/console "Components Mounted!"))

(defn init! []
  (rf/dispatch [:app/initialize])
  (ws/connect! (str "ws://" (.-host js/location) "/ws")
               handle-response!)
  (ajax/load-interceptors!)
  (mount-components))
