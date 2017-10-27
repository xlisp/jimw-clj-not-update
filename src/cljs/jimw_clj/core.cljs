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
            [cljsjs.highlight.langs.java])
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

(defn get-blog-list
  [q offset op-fn]
  (go (let [response
            (<!
             (http/get (api-root "/blogs")
                       {:with-credentials? false
                        :query-params {:q q :limit 10 :offset (* offset 10)}}))]
        (let [data (:body response)]
          (op-fn data)))))

(def swap-blog-list
  (fn [data]
    (->
     (map (fn [li]
            (do
              (swap! blog-list assoc (:id li)
                     {:name (:name li) :content (:content li)})
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
     :on-click #(reset! collapsed? true)} title]])

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
         [nav-link "#/about" "About" :about collapsed?]]]])))

(defn about-page []
  [:div.container
   [:div.row
    [:div.col-md-12
     [:img {:src (str js/context "/img/warning_clojure.png")}]]]])

(defn md-render [name content]
  [:div.container
   [:div.row>div.col-sm-12
    [:h1 name]
    [:div {:dangerouslySetInnerHTML
           {:__html (js/marked content)}}]
    [:hr {:align "center" :width "100%" :color "#987cb9" :size "1"}]]])

(defn home-page []
  [:div.container
   (for [blog @blog-list]
     (md-render
      (:name (last blog))
      (:content (last blog))))])

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
