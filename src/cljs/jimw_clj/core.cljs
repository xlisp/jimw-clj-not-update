(ns jimw-clj.core
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [reagent.core :as r]
            [clojure.core.async :as async :refer [<! >!]]
            [reagent.session :as session]
            [secretary.core :as secretary :include-macros true]
            [goog.events :as events]
            [goog.history.EventType :as HistoryEventType]
            ;;[markdown.core :refer [md->html]]
            ;;[jimw-clj.ajax :refer [load-interceptors!]]
            ;;[ajax.core :refer [GET POST]]
            [cljs-http.client :as http]
            [cljsjs.marked]
            [cljsjs.highlight]
            [cljsjs.highlight.langs.1c]
            [cljsjs.highlight.langs.abnf]
            [cljsjs.highlight.langs.accesslog]
            [cljsjs.highlight.langs.actionscript]
            [cljsjs.highlight.langs.ada]
            [cljsjs.highlight.langs.apache]
            [cljsjs.highlight.langs.applescript]
            [cljsjs.highlight.langs.arduino]
            [cljsjs.highlight.langs.armasm]
            [cljsjs.highlight.langs.asciidoc]
            [cljsjs.highlight.langs.aspectj]
            [cljsjs.highlight.langs.autohotkey]
            [cljsjs.highlight.langs.autoit]
            [cljsjs.highlight.langs.avrasm]
            [cljsjs.highlight.langs.awk]
            [cljsjs.highlight.langs.axapta]
            [cljsjs.highlight.langs.bash]
            [cljsjs.highlight.langs.basic]
            [cljsjs.highlight.langs.bnf]
            [cljsjs.highlight.langs.brainfuck]
            [cljsjs.highlight.langs.cal]
            [cljsjs.highlight.langs.capnproto]
            [cljsjs.highlight.langs.ceylon]
            [cljsjs.highlight.langs.clean]
            [cljsjs.highlight.langs.clojure]
            [cljsjs.highlight.langs.clojure-repl]
            [cljsjs.highlight.langs.cmake]
            [cljsjs.highlight.langs.coffeescript]
            [cljsjs.highlight.langs.coq]
            [cljsjs.highlight.langs.cos]
            [cljsjs.highlight.langs.cpp]
            [cljsjs.highlight.langs.crmsh]
            [cljsjs.highlight.langs.crystal]
            [cljsjs.highlight.langs.cs]
            [cljsjs.highlight.langs.csp]
            [cljsjs.highlight.langs.css]
            [cljsjs.highlight.langs.d]
            [cljsjs.highlight.langs.dart]
            [cljsjs.highlight.langs.delphi]
            [cljsjs.highlight.langs.diff]
            [cljsjs.highlight.langs.django]
            [cljsjs.highlight.langs.dns]
            [cljsjs.highlight.langs.dockerfile]
            [cljsjs.highlight.langs.dos]
            [cljsjs.highlight.langs.dsconfig]
            [cljsjs.highlight.langs.dts]
            [cljsjs.highlight.langs.dust]
            [cljsjs.highlight.langs.ebnf]
            [cljsjs.highlight.langs.elixir]
            [cljsjs.highlight.langs.elm]
            [cljsjs.highlight.langs.erb]
            [cljsjs.highlight.langs.erlang]
            [cljsjs.highlight.langs.erlang-repl]
            [cljsjs.highlight.langs.excel]
            [cljsjs.highlight.langs.fix]
            [cljsjs.highlight.langs.flix]
            [cljsjs.highlight.langs.fortran]
            [cljsjs.highlight.langs.fsharp]
            [cljsjs.highlight.langs.gams]
            [cljsjs.highlight.langs.gauss]
            [cljsjs.highlight.langs.gcode]
            [cljsjs.highlight.langs.gherkin]
            [cljsjs.highlight.langs.glsl]
            [cljsjs.highlight.langs.go]
            [cljsjs.highlight.langs.golo]
            [cljsjs.highlight.langs.gradle]
            [cljsjs.highlight.langs.groovy]
            [cljsjs.highlight.langs.haml]
            [cljsjs.highlight.langs.handlebars]
            [cljsjs.highlight.langs.haskell]
            [cljsjs.highlight.langs.haxe]
            [cljsjs.highlight.langs.hsp]
            [cljsjs.highlight.langs.htmlbars]
            [cljsjs.highlight.langs.http]
            [cljsjs.highlight.langs.hy]
            [cljsjs.highlight.langs.inform7]
            [cljsjs.highlight.langs.ini]
            [cljsjs.highlight.langs.irpf90]
            [cljsjs.highlight.langs.java]
            [cljsjs.highlight.langs.javascript]
            [cljsjs.highlight.langs.jboss-cli]
            [cljsjs.highlight.langs.json]
            [cljsjs.highlight.langs.julia]
            [cljsjs.highlight.langs.julia-repl]
            [cljsjs.highlight.langs.kotlin]
            [cljsjs.highlight.langs.lasso]
            [cljsjs.highlight.langs.ldif]
            [cljsjs.highlight.langs.leaf]
            [cljsjs.highlight.langs.less]
            [cljsjs.highlight.langs.lisp]
            [cljsjs.highlight.langs.livecodeserver]
            [cljsjs.highlight.langs.livescript]
            [cljsjs.highlight.langs.llvm]
            [cljsjs.highlight.langs.lsl]
            [cljsjs.highlight.langs.lua]
            [cljsjs.highlight.langs.makefile]
            [cljsjs.highlight.langs.markdown]
            [cljsjs.highlight.langs.mathematica]
            [cljsjs.highlight.langs.matlab]
            [cljsjs.highlight.langs.maxima]
            [cljsjs.highlight.langs.mel]
            [cljsjs.highlight.langs.mercury]
            [cljsjs.highlight.langs.mipsasm]
            [cljsjs.highlight.langs.mizar]
            [cljsjs.highlight.langs.mojolicious]
            [cljsjs.highlight.langs.monkey]
            [cljsjs.highlight.langs.moonscript]
            [cljsjs.highlight.langs.n1ql]
            [cljsjs.highlight.langs.nginx]
            [cljsjs.highlight.langs.nimrod]
            [cljsjs.highlight.langs.nix]
            [cljsjs.highlight.langs.nsis]
            [cljsjs.highlight.langs.objectivec]
            [cljsjs.highlight.langs.ocaml]
            [cljsjs.highlight.langs.openscad]
            [cljsjs.highlight.langs.oxygene]
            [cljsjs.highlight.langs.parser3]
            [cljsjs.highlight.langs.perl]
            [cljsjs.highlight.langs.pf]
            [cljsjs.highlight.langs.php]
            [cljsjs.highlight.langs.pony]
            [cljsjs.highlight.langs.powershell]
            [cljsjs.highlight.langs.processing]
            [cljsjs.highlight.langs.profile]
            [cljsjs.highlight.langs.prolog]
            [cljsjs.highlight.langs.protobuf]
            [cljsjs.highlight.langs.puppet]
            [cljsjs.highlight.langs.purebasic]
            [cljsjs.highlight.langs.python]
            [cljsjs.highlight.langs.q]
            [cljsjs.highlight.langs.qml]
            [cljsjs.highlight.langs.r]
            [cljsjs.highlight.langs.rib]
            [cljsjs.highlight.langs.roboconf]
            [cljsjs.highlight.langs.routeros]
            [cljsjs.highlight.langs.rsl]
            [cljsjs.highlight.langs.ruby]
            [cljsjs.highlight.langs.ruleslanguage]
            [cljsjs.highlight.langs.rust]
            [cljsjs.highlight.langs.scala]
            [cljsjs.highlight.langs.scheme]
            [cljsjs.highlight.langs.scilab]
            [cljsjs.highlight.langs.scss]
            [cljsjs.highlight.langs.shell]
            [cljsjs.highlight.langs.smali]
            [cljsjs.highlight.langs.smalltalk]
            [cljsjs.highlight.langs.sml]
            [cljsjs.highlight.langs.sqf]
            [cljsjs.highlight.langs.sql]
            [cljsjs.highlight.langs.stan]
            [cljsjs.highlight.langs.stata]
            [cljsjs.highlight.langs.step21]
            [cljsjs.highlight.langs.stylus]
            [cljsjs.highlight.langs.subunit]
            [cljsjs.highlight.langs.swift]
            [cljsjs.highlight.langs.taggerscript]
            [cljsjs.highlight.langs.tap]
            [cljsjs.highlight.langs.tcl]
            [cljsjs.highlight.langs.tex]
            [cljsjs.highlight.langs.thrift]
            [cljsjs.highlight.langs.tp]
            [cljsjs.highlight.langs.twig]
            [cljsjs.highlight.langs.typescript]
            [cljsjs.highlight.langs.vala]
            [cljsjs.highlight.langs.vbnet]
            [cljsjs.highlight.langs.vbscript]
            [cljsjs.highlight.langs.vbscript-html]
            [cljsjs.highlight.langs.verilog]
            [cljsjs.highlight.langs.vhdl]
            [cljsjs.highlight.langs.vim]
            [cljsjs.highlight.langs.x86asm]
            [cljsjs.highlight.langs.xl]
            [cljsjs.highlight.langs.xml]
            [cljsjs.highlight.langs.xquery]
            [cljsjs.highlight.langs.yaml]
            [cljsjs.highlight.langs.zephir]
            [jimw-clj.edit :as edit]
            [jimw-clj.edit-md :as edit-md]
            [jimw-clj.todos :as todos]
            [alandipert.storage-atom :refer [local-storage]]
            [myexterns.viz]
            [myexterns.wordcloud]
            [re-frame.core :as re-frame]
            [jimw-clj.events :as msg-events]
            [jimw-clj.subs :as subs]
            [jimw-clj.views :as views]
            [re-frame.core :as re-frame]
            [jimw-clj.something :as something])
  (:import goog.History))

(declare blog-list)

(defn json-parse
  [json]
  (->
   (.parse js/JSON json)
   (js->clj :keywordize-keys true)))

(declare pcm-ip)

(re-frame/reg-event-db
 :msg/push-all
 (fn [db [_ {:keys [msgs]}]]
   #_(prn (str "----" msgs))
   ;;
   (let [{:keys [kind table columnnames columnvalues oldkeys]} (first (:change (json-parse msgs)))
         {:keys [id blog parid content created_at updated_at done
                 sort_id wctags app_id file islast percent begin mend origin_content]}
         (zipmap (map keyword columnnames) columnvalues)]
     (cond
       ;; 1. todos表的Websocket的同步
       (= table "todos")
       ;;(prn (str "------" content))
       (cond (= kind "insert")
             ;;
             (do (prn (str id "------insert" content))
                 (swap! blog-list update-in
                        [blog :todos]
                        #(assoc % sort_id {:id sort_id :sort_id id
                                           :search true                                                   
                                           :parid parid
                                           :content content}))
                 )
             ;;
             (= kind "update")
             (do (prn (str id "------update" content))
                 (swap! blog-list update-in [blog :todos sort_id :content] (fn [x] content))
                 )             
             (= kind "delete")
             (do (prn
                  (str
                   (first (:keyvalues oldkeys)) "------delete" content))                 
                 #_(swap! blog-list update-in
                          [blog :todos] #(dissoc % (first (:keyvalues oldkeys))))
                 )
             :else (prn "todos other operation"))

       ;; 2. pcmip表的Websocket的同步
       (= table "pcmip")
       (reset! pcm-ip (second columnvalues))
       ;; 3. 其他表的更新
       :else (prn (str table " update"))
       )
     )
   ;;
   #_(assoc db :msgs msgs)
   ))

(.setOptions js/marked
             (clj->js
              {:table true
               :highlight #(.-value (.highlightAuto js/hljs %))}))

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
(def pcm-ip (local-storage (r/atom "0.0.0.0") :pcm-ip))

(defonce search-key (r/atom ""))

(defonce search-viz-en (r/atom (sorted-map-by >)))
(defonce search-wolframalpha-en (r/atom {}))
(defonce focus-bdsug-blog-id (r/atom nil))

;; (viz-string "digraph { a -> b; }")
;; Chrome: jimw_clj.core.viz_string("digraph { a -> b; }")
;; (let [graph (.querySelector js/document "#gv-output-9845")] (.appendChild graph (viz-string "digraph { a -> b; }")))
(defn viz-string
  [st]
  (.-documentElement
   (.parseFromString
    (js/DOMParser.)
    (->
     (js/Viz st)
     (clojure.string/replace-first #"width=\"\d+pt\"" "")
     (clojure.string/replace-first #"height=\"\d+pt\"" ""))
    "image/svg+xml")))

(defonce source-names (r/atom []))
(defonce active-source (r/atom "BLOG"))

(defonce source-names-list-init
  (go (let [response
            (<!
             (http/get (api-root "/source-nams")
                       {:with-credentials? false
                        :headers {"jimw-clj-token" @api-token}
                        :query-params {}}))]
        (let [data (:body response)]
          (reset! active-source (first data))
          (reset! source-names data)))))

(defn select-source-page []
  (let [page
        [:select#select-source
         {:on-change
          (fn [e]
            (let [selected-val (.-value (. js/document (getElementById "select-source")))]
              (reset! active-source selected-val)))
          :value @active-source}
         (map
          (fn [opt] [:option {:value opt} opt])
          @source-names)]]
    page))

;;
(defonce project-names (r/atom []))
(defonce active-project (r/atom ""))

(defonce project-names-list-init
  (go (let [response
            (<!
             (http/get (api-root "/project-nams")
                       {:with-credentials? false
                        :headers {"jimw-clj-token" @api-token}
                        :query-params {}}))]
        (let [data (:body response)]
          (reset! active-project (first data))
          (reset! project-names data)))))

(defn select-project-page []
  (let [page
        [:select#select-project
         {:on-change
          (fn [e]
            (let [selected-val (.-value (. js/document (getElementById "select-project")))]
              (reset! active-project selected-val)))
          :value @active-project}
         (map
          (fn [opt] [:option {:value opt} opt])
          @project-names)]]
    page))
;;

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
  (go (let [{:keys [status body]}
            (<!
             (http/get (api-root "/blogs")
                       {:with-credentials? false
                        :headers {"jimw-clj-token" @api-token}
                        :query-params
                        (merge {:q q :limit 5
                                :offset (* offset 5)
                                :source @active-source}
                               (if (and (seq @active-project)
                                        (or (= @active-source "SEMANTIC_SEARCH") (= @active-source "REVERSE_ENGINEERING")))
                                 {:project @active-project}
                                 {}))}))]
        (if (= status 200)
          (op-fn body)
          (js/alert "Unauthorized !")))))

(defn update-blog
  [id name content op-fn]
  (go (let [{:keys [status body]}
            (<!
             (http/put (str (api-root "/update-blog/") id)
                       {:headers {"jimw-clj-token" @api-token}
                        :json-params
                        {:name name :content content}}))]
        (if (= status 200)
          (op-fn body)
          (js/alert "Unauthorized !")))))

(defn create-default-blog
  [op-fn]
  (go (let [response
            (<!
             (http/post (api-root "/create-blog")
                        {:headers {"jimw-clj-token" @api-token}
                         :json-params
                         {:name (str "给我一个lisp的支点" (js/Date.now)) :content "### 我可以撬动整个地球!"}}))]
        (let [data (:body response)]
          (swap! blog-list assoc (:id data)
                 {:id (:id data) :content (:content data) :name (:name data)
                  :todos (sorted-map-by >)})
          (op-fn)))))

(def swap-blog-list
  (fn [data]
    (->
     (map (fn [li]
            (do
              (swap! blog-list assoc (:id li)
                     {:id (:id li) :name (:name li) :content (:content li)
                      :todos (into
                              (sorted-map-by >)
                              (map (fn [x] (vector (:id x)
                                                   (merge x {:search true})
                                                   ;;x
                                                   )) (:todos li)))})
              (:id li))) data) str prn)))

(defonce blog-list-init
  (get-blog-list "" @page-offset
                 (fn [data] (swap-blog-list data))))

(def is-end (atom true))

(set!
 js/window.onscroll
 #(if (and (is-page-end-m-pc) @is-end (= (session/get :page) :home))
    (do
      (swap! page-offset inc)
      (reset! is-end false)
      (get-blog-list @search-key @page-offset
                     (fn [data]
                       (swap-blog-list data)
                       (reset! is-end true))))
    nil))

(declare record-event)
(declare searchbar-mode)

;; TODOS: Emacs 的键位设计用在CLJS身上
(set!
 js/window.onkeydown
 (fn [e]
   (let [keycode (.-keyCode e)
         ;; 0~9 => 48~57
         ctrlkey (.-ctrlKey e)
         ;; true or false
         metakey (.-metaKey e)]
     ;; Ctrl的组合键
     (if (and ctrlkey (not= keycode 17))
       (cond
         ;; 数字键
         ((set (range 47 58)) keycode)
         (let [key-num (- keycode 48)
               content (get @search-wolframalpha-en (keyword (str key-num)))]
           (prn (str "数字键" key-num ", " content))
           (something/copyToClipboard content))
         (= 71 keycode)
         (let [_ (prn "11111111888888")
               selector (.getSelection js/window)
               _ (prn "2222222888888")
               select-stri (.toString selector)]
           ;;(js/alert (str "1111133344" select-stri))
           (prn select-stri)
           ;;(prn (str "谷歌: " select-stri))
           (reset! searchbar-mode false)
           (set! (.-value (.getElementById js/document "google-input")) (str select-stri))
           (record-event "search-google-event" (str select-stri) identity)
           (.click (.getElementById js/document "google-input-button"))
           )
         ;;
         :else (prn keycode))
       nil)
     ;; Meta的组合键TODO
     )
   )
 )

(defn nav-link [uri title page collapsed?]
  [:li.nav-item
   {:class (when (= page (session/get :page)) "active")}
   [:a.nav-link
    {:href uri
     :on-click #(do
                  (if (= page :show)
                    (set! (.-display (.-style (. js/document (getElementById "wordcloud")))) "block")
                    (set! (.-display (.-style (. js/document (getElementById "wordcloud")))) "none"))
                  (cond (= page :create-blog)
                        (create-default-blog
                         (fn [] (set! (.. js/window -location -href) (api-root ""))))
                        (= page :logout) (reset! api-token "")
                        :else
                        (reset! collapsed? true)))} title]])

(defn record-event
  [event_name event_data op-fn]
  (go (let [response
            (<!
             (http/post (api-root "/record-event")
                        {:headers {"jimw-clj-token" @api-token}
                         :json-params
                         {:event_name event_name :event_data event_data}}))]
        (let [data (:body response)]
          (op-fn data)))))

(defn search-map-zh2en
  [q op-fn]
  (go (let [{:keys [status body]}
            (<!
             (http/get (api-root "/search-map-zh2en")
                       {:with-credentials? false
                        :headers {"jimw-clj-token" @api-token}
                        :query-params {:q q}}))]
        (if (= status 200)
          (op-fn (:data body))
          (js/alert "Unauthorized !")))))

(defonce searchbar-mode (atom true))
  
(defn searchbar []
  (let [search-str (r/atom "")
        google-q (r/atom "")
        github-q (r/atom "")
        youtube-q (r/atom "")
        wolfram-alpha-q (r/atom "")
        pcm-ip-txt (r/atom "")
        search-fn (fn []
                    (do
                      (reset! blog-list (sorted-map-by >))
                      (reset! page-offset 0)
                      (reset! search-key @search-str)
                      (get-blog-list
                       @search-str @page-offset
                       (fn [data]
                         (swap-blog-list data)))
                      (set! (.-title js/document) @search-str)
                      (record-event "search-blog-event" @search-str identity)))
        append-stri (r/atom "")]
    (fn []
      [:div
       [:div#adv-search.input-group.search-margin
        [:input {:type "text", :class "form-control", :placeholder "Search for blogs"
                 :on-change #(reset! search-str (-> % .-target .-value))
                 :on-key-down #(case (.-which %)
                                 13 (search-fn)
                                 nil)}]
        [:div {:class "input-group-btn"}
         [:div {:class "btn-group", :role "group"}
          [:div {:class "dropdown dropdown-lg"}]
          [:button {:type "button", :class "btn btn-primary"
                    :on-click search-fn}
           [:span {:class "glyphicon glyphicon-search", :aria-hidden "true"}]]]]]
       ;;
       [:form {:target "_blank", :action "http://www.google.com/search", :method "get"} 
        [:input {:id "google-input"
                 :type "text"
                 :on-change #(do
                               (reset! searchbar-mode true)
                               (reset! google-q (-> % .-target .-value)))
                 :on-key-down #(case (.-which %)
                                 13 (if (and (not-empty @google-q) @searchbar-mode)
                                      (record-event "search-google-event" @google-q identity)
                                      nil)
                                 nil)
                 :on-blur #(reset! searchbar-mode false)
                 :name "q"}] 
        [:input {:type "submit", :value "Google"
                 :id "google-input-button"
                 #_:on-click #_(if (and (not-empty @google-q) @searchbar-mode)
                              (record-event "search-google-event" @google-q identity)
                              nil)}]]
       ;;
       [:div.viz-container
        [:div#adv-search.input-group.search-margin
         [:form {:target "_blank", :action "https://www.wolframalpha.com/input", :method "get"}
          [:input {:id "wolfram-alpha-input"
                   :type "text"
                   :on-change #(do
                                 (reset! wolfram-alpha-q (-> % .-target .-value))
                                 (search-map-zh2en @wolfram-alpha-q (fn [data] (reset! search-wolframalpha-en data))))
                   :on-key-down #(case (.-which %)
                                   13 (do (prn 111) (record-event "search-wolfram-alpha-event" @wolfram-alpha-q identity))
                                   nil)
                   :name "i"}]
          [:input {:type "submit", :value "WolframAlpha"
                   :on-click #(record-event "search-wolfram-alpha-event" @wolfram-alpha-q identity)}]]
         (let [alpha-input (.getElementById js/document "wolfram-alpha-input")
               google-input (.getElementById js/document "google-input")]
           [:ul
            (if (nil? @focus-bdsug-blog-id)
              (for [item @search-wolframalpha-en]
                [:li {:on-click #(do (reset! append-stri (str (last item)))
                                     (set! (.-value alpha-input) (str  (.-value alpha-input) " "  (str (last item))))
                                     (set! (.-value google-input) (str  (.-value google-input) " "  (str (last item))))
                                     )}
                 (str (first item) ". " (last item))]
                ))
            ]
           )
         ]]
       ;;[:p]
       [:form {:target "_blank", :action "https://github.com/search?utf8=✓", :method "get"} 
        [:input {:type "text"
                 :on-change #(reset! github-q (-> % .-target .-value))
                 :on-key-down #(case (.-which %)
                                 13 (record-event "search-github-event" @github-q identity)
                                 nil)
                 :name "q"}] 
        [:input {:type "submit" :value "Github"
                 :on-click #(record-event "search-github-event" @github-q identity)}]]
       [:p]
       [:form {:target "_blank", :action "https://www.youtube.com/results", :method "get"}
        [:input {:type "text"
                 :on-change #(reset! youtube-q (-> % .-target .-value))
                 :on-key-down #(case (.-which %)
                                 13 (record-event "search-youtube-event" @youtube-q identity)
                                 nil)
                 :name "search_query"}]
        [:input {:type "submit" :value "Youtube"
                 :on-click #(record-event "search-youtube-event" @youtube-q identity)}]]
       ;;
       [:h6 "pcm ip: " @pcm-ip ", "
        [:a {:id "download-api-token" :href (str "data:text/plain," @api-token) :download "api-token.txt" :target "_blank"} "token"]]
       [:input {:type "text"
                :value @pcm-ip-txt
                :on-change #(reset! pcm-ip-txt (-> % .-target .-value))
                :on-key-down #(case (.-which %)
                                13 (do (reset! pcm-ip @pcm-ip-txt)
                                       (js/alert (str "更新pcm播放地址为" @pcm-ip)))
                                nil)}]
       [:p]
       [select-source-page]
       (if (or (= @active-source "SEMANTIC_SEARCH") (= @active-source "REVERSE_ENGINEERING"))
         [select-project-page])
       [:p]
       ])))

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
         [nav-link "#/show" "Show" :show collapsed?]
         [nav-link "#/viz" "Viz" :viz collapsed?]
         [nav-link "#/about" "About" :about collapsed?]
         [nav-link "#/" "New" :create-blog collapsed?]
         [nav-link "#/logout" "Logout" :logout collapsed?]]]])))

(defn about-page []
  [:div.container.about-margin
   [:div.row
    [:div.col-sm-4
     [:img.steve-chan-img {:src "/img/steve-chan.jpeg"}]]
    [:div.col-sm-6
     [:h1 "About Steve Chan"]
     [:p "My name is Steve Chan and I'm a Clojure/R/ELisp/Ruby hacker from BeiJing, China."]
     [:p "I love Wing Chun. In the Source I trust !"]
     [:h4 "features"]
     [:li "用机器学习来机器学习"]
     [:li "用GraphViz树来做决策树训练工具"]
     [:li "用标签云来做贝叶斯训练工具"]
     [:li "整合手机APP及微信浏览器数据流"]
     [:li "整合Chrome插件数据流"]
     [:li "整合Emacs数据流"]
     [:li "整合输入法数据流"]
     [:li "整合咏春训练数据流"]]]])

(defn logout-page []
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
                  (set! (.. js/window -location -href) (api-root ""))
                  (go (async/<! (async/timeout 2000))
                      (.click (. js/document (getElementById "download-api-token")))))
                (js/alert "username or password is error!")))))} "Login"]]]]))

(defn blog-name-save [id name]
  (do
    (swap! blog-list assoc-in [id :name] name)
    (update-blog id name nil #(prn %))))

(defn blog-content-save [id content]
  (do
    (swap! blog-list assoc-in [id :content] content)
    (update-blog id nil content #(prn %))))

(defn get-digraph
  [blog op-fn]
  (go (let [{:keys [status body]}
            (<!
             (http/get (api-root (str "/todos-" blog ".gv"))
                       {:with-credentials? false
                        :headers {"jimw-clj-token" @api-token}
                        :query-params {}}))]
        (if (= status 200)
          (op-fn body)
          (js/alert "Unauthorized !")))))

(defn tree-todo-generate [blog]
  (go (let [response
            (<!
             (http/post (api-root "/tree-todo-generate")
                        {:with-credentials? false
                         :headers {"jimw-clj-token" @api-token}
                         :query-params {:blog blog}}))])))

(defn tree-todo-generate-new
  [blog op-fn]
  (go (let [{:keys [status body]}
            (<!
             (http/post (api-root "/tree-todo-generate-new")
                        {:with-credentials? false
                         :headers {"jimw-clj-token" @api-token}
                         :query-params {:blog blog}}))]
        (if (= status 200)
          (op-fn (:data body))
          (js/alert "Unauthorized !")))))

(defn qrcode-generate
  [blog op-fn]
  (go (let [{:keys [status body]}
            (<!
             (http/post (api-root "/qrcode-generate")
                        {:with-credentials? false
                         :headers {"jimw-clj-token" @api-token}
                         :query-params {:blog blog}}))]
        (if (= status 200)
          (op-fn body)
          (js/alert "Unauthorized !")))))

(defn search-sqldots
  [q op-fn]
  (go (let [{:keys [status body]}
            (<!
             (http/get (api-root "/search-sqldots")
                       {:with-credentials? false
                        :headers {"jimw-clj-token" @api-token}
                        :query-params {:q q}}))]
        (if (= status 200)
          (op-fn (:data body))
          (js/alert "Unauthorized !")))))

(defn get-blog-wctags
  [id op-fn scaling show-count]
  (go (let [{:keys [status body]}
            (<!
             (http/get (api-root "/get-blog-wctags")
                       {:with-credentials? false
                        :headers {"jimw-clj-token" @api-token}
                        :query-params {:id id}}))]
        (if (= status 200)
          (op-fn (vec (map (fn [item] (vector (name (first item)) (* (last item) scaling)))
                           (take show-count (:data body)))))
          (js/alert "Unauthorized !")))))

(defn md-render [id name content]
  (let [editing (r/atom false)]
    [:div.container
     [:div.row>div.col-sm-12
      [edit/blog-name-item {:id id :name name :save-fn blog-name-save}]
      [edit-md/blog-content-item {:id id :name content :save-fn blog-content-save}]
      [todos/todo-app blog-list id search-wolframalpha-en focus-bdsug-blog-id]
      [:div
       ;; 打开本地的Viz临时使用局部树
       [:button.btn.tree-btn
        {:on-click
         #(do (js/alert "Update...")
              (tree-todo-generate id))} "Generate"]
       [:a.btn.margin-download
        {:href (str "/todos-" id ".gv")
         :download (str "past_" id "_navs.zip")} "Download"]
       [:button.btn.margin-download
        {:on-click #(let [graph (.querySelector js/document (str "#gv-output-" id))
                          svg (.querySelector graph "svg")]
                      (do
                        (if svg (.removeChild graph svg) ())
                        (get-digraph id
                                     (fn [digraph-str]
                                       (.appendChild
                                        graph
                                        (viz-string digraph-str))))))} "Viz"]
       ;; 
       ;; 生产环境测试viz.js已ok
       [:button.btn.margin-download
        {:on-click #(let [graph (.querySelector js/document (str "#gv-output-" id))
                          svg (.querySelector graph "svg")]
                      (do
                        (if svg (.removeChild graph svg) ())
                        (tree-todo-generate-new
                         id
                         (fn [digraph-str]
                           (.appendChild
                            graph
                            (viz-string digraph-str))))))} "NewViz"]
       [:button.btn.margin-download
        {:on-click #(let [elem (.getElementById js/document (str "wordcloud-" id))]
                      (set! (.-display (.-style elem)) "block")
                      (get-blog-wctags
                       id
                       (fn [wctags]
                         (window.WordCloud
                          elem
                          (clj->js
                           {:list wctags}))) 5 30))} "WordCloud"]
       [:button.btn.margin-download
        {:on-click #(qrcode-generate
                     id
                     (fn [data]
                       (let [img-ele (.createElement js/document "img")
                             qrcode-div (.querySelector js/document (str "#qrcode-" id))]
                         (set! (.-src img-ele ) (str "/qrcode/" (:file data)))
                         (.appendChild qrcode-div img-ele)
                         )))
         } "QRCode"]
       ]
      [:br]
      [:div.gvoutput {:id (str "gv-output-" id)}]
      [:canvas.wcanvas {:id (str "wordcloud-" id)}]
      [:div {:id (str "qrcode-" id) :style {:width "20%"}}]
      [:hr]]]))

(defn home-page []
  [:div.container.app-margin
   (if (seq @api-token)
     (for [blog @blog-list]
       [:div
        (md-render
         (:id (last blog))
         (:name (last blog))
         (:content (last blog)))])
     [:h3.please-login "please login"])])



(defn word-cloud-did-mount [this]
  (get-blog-wctags
   25125
   (fn [wctags]
     (window.WordCloud
      (r/dom-node this)
      (clj->js { :list wctags })
      #(do
         (js/alert %)))) 50 30))

(defn word-cloud-create-class []
  (r/create-class {:reagent-render
                   (fn []
                     [:div
                      {:style
                       {:height    "2500px"
                        :margin    "0 auto"}}])
                   :component-did-mount
                   (fn [this]
                     (word-cloud-did-mount this))}))

(defn update-wordcloud-component []
  (set! (.-innerHTML (. js/document (getElementById "wordcloud"))) "")
  (r/render-component [word-cloud-create-class]
                      (. js/document (getElementById "wordcloud"))))

(defonce scaling (r/atom 30))
(defonce show-count (r/atom 50))

(defn show-page []
  [:div.container.app-margin
   [:div.row
    [:div.col-sm-2
     [:h6 "文章"]
     [:input {:type "number"}]]
    [:div.col-sm-5
     [:h6 "放大倍数 " @scaling]
     [:input {:type "range" :min 5 :max 50
              :style {:width "100%"}
              :on-change (fn [e] (reset! scaling (.. e -target -value)))}]
     [:h6 "最大显示词数量 " @show-count]
     [:input {:type "range" :min 10 :max 100
              :style {:width "100%"}
              ;;:show-count @show-count
              :on-change (fn [e] (reset! show-count (.. e -target -value)))}]]
    [:div.col-sm-2
     [:button.btn.btn-primary "Generate"]]]
   [:h1 
    {:on-click #(do
                  (js/alert (str "====" @scaling "====" @show-count))
                  (update-wordcloud-component))
     }
    "."
    ]
   ]
  )

(defonce search-viz-str (atom ""))

(defn search-mapen 
  [q op-fn]
  (go (let [{:keys [status body]}
            (<!
             (http/get (api-root "/search-mapen")
                       {:with-credentials? false
                        :headers {"jimw-clj-token" @api-token}
                        :query-params {:q q}}))]
        (if (= status 200)
          (op-fn (:data body))
          (js/alert "Unauthorized !")))))

(defonce mapen-show (atom ""))

(defn viz-page []
  (let [viz-fn #(let [graph (.querySelector js/document "#gv-output-sql")
                      svg (.querySelector graph "svg")]
                  (do
                    (if svg (.removeChild graph svg) ())
                    (search-sqldots
                     %
                     (fn [digraph-str]
                       (.appendChild
                        graph
                        (viz-string digraph-str))))))]
    [:div.viz-container
     #_[:div.viz-search-logo
        [:h2 "Viz"]]
     [:div#adv-search.input-group.search-margin
      [:input {:type "text", :class "form-control", :placeholder "Search"
               :on-change #(do
                             (reset! search-viz-str (-> % .-target .-value))
                             (search-mapen @search-viz-str (fn [data] (reset! search-viz-en data))))
               :on-key-down #(case (.-which %)
                               13 (viz-fn @search-viz-str)
                               nil)}]
      [:ul
       (for [item @search-viz-en]
         [:li (str (last item))])]
      [:div {:class "input-group-btn"}
       [:div {:class "btn-group", :role "group"}
        [:div {:class "dropdown dropdown-lg"}]
        #_[:button {:type "button", :class "btn btn-primary"
                    :on-click #(viz-fn @search-viz-str)}
           [:span {:class "glyphicon glyphicon-search", :aria-hidden "true"}]]]]]
     [:br]
     [:div.gvoutput {:id "gv-output-sql"}]]))

;; 新增路由区域, 配合navbar使用
(def pages
  {:home #'home-page
   :about #'about-page
   :logout #'logout-page
   :show #'show-page
   :viz #'viz-page})

(defn page []
  [(pages (session/get :page))])

;; -------------------------
;; Routes
(secretary/set-config! :prefix "#")

(secretary/defroute "/" []
  (session/put! :page :home))

(secretary/defroute "/about" []
  (session/put! :page :about))

(secretary/defroute "/logout" []
  (session/put! :page :logout))

(secretary/defroute "/show" []
  (session/put! :page :show))

(secretary/defroute "/viz" []
  (session/put! :page :viz))

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

(defn mount-components []
  (r/render [#'navbar] (.getElementById js/document "navbar"))
  (r/render [#'searchbar] (.getElementById js/document "searchbar"))
  (r/render [#'page] (.getElementById js/document "app"))
  (r/render [views/main-view] (.getElementById js/document "msg")))

(defn init! []
  ;;(load-interceptors!)
  (hook-browser-navigation!)
  (re-frame/dispatch-sync [:db/initialize])
  (re-frame/dispatch-sync [:sente/connect])
  (mount-components))


#_(for [item (get-in @blog-list [37581 :todos])]
  (do
    (let [{:keys [content id] :as todo} (last item)]
      (prn content)
      )
    )
  )


#_(swap! @blog-list update-in [37581 :todos]                            
       ;;@(get-in @blog-list [37581 :todos])
       ;;(sorted-map)
       (fn [x] (sorted-map))
       )

;; (swap! @blog-list update-in [37581 :todos 228 :content] (fn [x] "0000" ))

;; (something/hello) ;; => "Hey there from example.something JavaScript"
;; (something/getSelectionEndPosition) ;;  => #js {:x 246.125, :y 458}
;; (something/copyToClipboard "aaaaaa") <=> jimw_clj.something.copyToClipboard("aaaaaaadsadsa")
