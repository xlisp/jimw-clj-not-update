(ns jimw-clj.core
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [reagent.core :as r]
            [reagent.session :as session]
            [secretary.core :as secretary :include-macros true]
            [goog.events :as events]
            [goog.history.EventType :as HistoryEventType]
            [markdown.core :refer [md->html]]
            [jimw-clj.ajax :refer [load-interceptors!]]
            [ajax.core :refer [GET POST]]
            [cljs-http.client :as http]
            [cljsjs.marked]
            [cljsjs.highlight]
            [cljsjs.highlight.langs.clojure]
            [cljsjs.highlight.langs.ruby]
            [cljsjs.highlight.langs.java]
            [jimw-clj.edit :as edit]
            [jimw-clj.edit-md :as edit-md]
            [jimw-clj.todos :as todos]
            [alandipert.storage-atom :refer [local-storage]])
  (:import goog.History))

(.setOptions js/marked
             #js {:highlight (fn [code]
                               (.-value (.highlightAuto js/hljs code)))})

(defn api-root [url] (str (-> js/window .-location .-origin) url))
(defn s-height [] (.. js/document -body -scrollHeight))

(defn s-top [] (.. js/document -body -scrollTop))
(defn sd-top [] (.. js/document -documentElement -scrollTop))
(defn ss-top [] (.. js/window -pageYOffset))

(defn o-height [] (.. js/document -body -offsetHeight))

(defn is-page-end []
  (<=
   (- (s-height)
      (ss-top) #_(s-top))
   (o-height)))

(defn is-page-end-m-pc []
  (>=
   (+ (ss-top) (o-height) 60)
   (s-height)))

(defonce page-offset (r/atom 0))
(defonce blog-list (r/atom (sorted-map-by >)))
(def api-token (local-storage (r/atom "") :api-token))

(defn login
  [username password op-fn]
  (go (let [response
            (<!
             (http/post (api-root "/login")
                       {:with-credentials? false
                        :query-params {:username username :password password}}))]
        (let [data (:body response)]
          (op-fn data)))))

(defn get-blog-list
  [q offset op-fn]
  (go (let [response
            (<!
             (http/get (api-root "/blogs")
                       {:with-credentials? false
                        :headers {"jimw-clj-token" @api-token}
                        :query-params {:q q :limit 10 :offset (* offset 10)}}))]
        (let [data (:body response)]
          (op-fn data)))))

(defn update-blog
  [id name content op-fn]
  (go (let [response
            (<!
             (http/put (str (api-root "/update-blog/") id)
                       {:headers {"jimw-clj-token" @api-token}
                        :json-params
                        {:name name :content content}}))]
        (let [data (:body response)]
          (op-fn data)))))

(defn create-default-blog
  []
  (go (let [response
            (<!
             (http/post (api-root "/create-blog")
                        {:headers {"jimw-clj-token" @api-token}
                         :json-params
                         {:name (str "给我一个lisp的支点" (js/Date.now)) :content "### 我可以撬动整个地球!"}}))]
        (let [data (:body response)]
          (swap! blog-list assoc (:id data) {:id (:id data) :content (:content data) :name (:name data)})))))

(def swap-blog-list
  (fn [data]
    (->
     (map (fn [li]
            (do
              (swap! blog-list assoc (:id li)
                     {:id (:id li) :name (:name li) :content (:content li)
                      :todos (into
                              (sorted-map-by >)
                              (map (fn [x] (vector (:id x) x)) (:todos li)))})
              (:id li))) data) str prn)))

(defonce blog-list-init
  (get-blog-list "" @page-offset
                 (fn [data] (swap-blog-list data))))

(set!
 js/window.onscroll
 #(if (is-page-end-m-pc)
    (do
      (swap! page-offset inc)
      (get-blog-list "" @page-offset swap-blog-list))
    nil))

(defn nav-link [uri title page collapsed?]
  [:li.nav-item
   {:class (when (= page (session/get :page)) "active")}
   [:a.nav-link
    {:href uri
     :on-click #(if (= page :create-blog)
                  (create-default-blog)
                  (reset! collapsed? true))} title]])

(defn navbar []
  (let [collapsed? (r/atom true)]
    (fn []
      [:nav.navbar.navbar-dark.bg-primary
       [:button.navbar-toggler.hidden-sm-up
        {:on-click #(swap! collapsed? not)} "☰"]
       [:div.collapse.navbar-toggleable-xs
        (when-not @collapsed? {:class "in"})
        [:a.navbar-brand {:href "#/"} "jimw-clj"]
        [:ul.nav.navbar-nav
         [nav-link "#/" "Home" :home collapsed?]
         [nav-link "#/about" "About" :about collapsed?]
         [nav-link "#/" "NewBlog" :create-blog collapsed?]]]])))

(defn about-page []
  (if-not (empty? @api-token)
    [:div.container
     [:div.row
      [:div.col-md-12
       [:img {:src (str js/context "/img/warning_clojure.png")}]]]]
    (let [username (r/atom "")
          password (r/atom "")]
      [:div {:class "main-login main-center"}
       [:form {:class "form-horizontal", :method "post", :action "#"}
        [:div {:class "form-group"}
         [:label {:for "name", :class "cols-sm-2 control-label"} "Your Name"]
         [:div {:class "cols-sm-10"}
          [:div {:class "input-group"}
           [:span {:class "input-group-addon"}
            [:i {:class "fa fa-user fa", :aria-hidden "true"}]]
           [:input {:type "text", :class "form-control", :name "name", :id "name", :placeholder "Enter your Name"
                    :on-change #(reset! username (-> % .-target .-value))}]]]]
        [:div {:class "form-group"}
         [:label {:for "confirm", :class "cols-sm-2 control-label"} "Confirm Password"]
         [:div {:class "cols-sm-10"}
          [:div {:class "input-group"}
           [:span {:class "input-group-addon"}
            [:i {:class "fa fa-lock fa-lg", :aria-hidden "true"}]]
           [:input {:type "password", :class "form-control", :name "confirm", :id "confirm", :placeholder "Confirm your Password"
                    :on-change #(reset! password (-> % .-target .-value))}]]]]
        [:div {:class "form-group"}
         [:button
          {:type "button", :class "btn btn-primary btn-lg btn-block login-button"
           :on-click
           (fn []
             (login
              @username @password
              (fn [data]
                (if (:token data)
                  (do
                    (js/alert "login success!")
                    (reset! api-token (:token data))
                    (set! (.. js/window -location -href) (api-root "")))
                  (js/alert "username or password is error!")))))} "Login"]]]])))

(defn blog-name-save [id name]
  (do
    (swap! blog-list assoc-in [id :name] name)
    (update-blog id name nil #(prn %))))

(defn blog-content-save [id content]
  (do
    (swap! blog-list assoc-in [id :content] content)
    (update-blog id nil content #(prn %))))

(defn md-render [id name content]
  (let [editing (r/atom false)]
    [:div.container
     [:div.row>div.col-sm-12
      [edit/blog-name-item {:id id :name name :save-fn blog-name-save}]
      [edit-md/blog-content-item {:id id :name content :save-fn blog-content-save}]
      [todos/todo-app blog-list id]
      [:hr {:align "center" :width "100%" :color "#987cb9" :size "1"}]]]))

(defn home-page []
  [:div.container
   (for [blog @blog-list]
     [:div
      (md-render
       (:id (last blog))
       (:name (last blog))
       (:content (last blog)))])])

(def pages
  {:home #'home-page
   :about #'about-page})

(defn page []
  [(pages (session/get :page))])

;; -------------------------
;; Routes
(secretary/set-config! :prefix "#")

(secretary/defroute "/" []
  (session/put! :page :home))

(secretary/defroute "/about" []
  (session/put! :page :about))

;; -------------------------
;; History
;; must be called after routes have been defined
(defn hook-browser-navigation! []
  (doto (History.)
    (events/listen
     HistoryEventType/NAVIGATE
     (fn [event]
       (secretary/dispatch! (.-token event))))
    (.setEnabled true)))

;; -------------------------
;; Initialize app
(defn fetch-docs! []
  (GET "/docs" {:handler #(session/put! :docs %)}))

(defn mount-components []
  (r/render [#'navbar] (.getElementById js/document "navbar"))
  (r/render [#'page] (.getElementById js/document "app")))

(defn init! []
  (load-interceptors!)
  (fetch-docs!)
  (hook-browser-navigation!)
  (mount-components))
