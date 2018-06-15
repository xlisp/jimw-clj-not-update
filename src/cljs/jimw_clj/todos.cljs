(ns jimw-clj.todos
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [reagent.core :as r :refer [atom]]
            [cljs-http.client :as http]
            [cljs.core.async :refer [<!]]
            [cemerick.url :refer (url url-encode)]
            [clojure.string :as str]
            [jimw-clj.something :as something]
            cljsjs.clipboard))

(defn api-root [url] (str (-> js/window .-location .-origin) url))

(defn get-api-token
  []
  (->
   (.getItem js/localStorage "[\"~#'\",\"~:api-token\"]")
   (clojure.string/split "\"")
   (get 3)))

(defn get-pcm-ip
  []
  (->
   (.getItem js/localStorage "[\"~#'\",\"~:pcm-ip\"]")
   (clojure.string/split "\"")
   (get 3)))

(def memoized-api-token (memoize get-api-token))

(defn record-event
  [event_name event_data op-fn]
  (go (let [response
            (<!
             (http/post (api-root "/record-event")
                        {:headers {"jimw-clj-token" (memoized-api-token)}
                         :json-params
                         {:event_name event_name :event_data event_data}}))]
        (let [data (:body response)]
          (op-fn data)))))

;; (get-todos-list 4857 #(-> (zipmap  (map :id %) %) prn))
(defn get-todos-list
  [blog op-fn]
  (go (let [response
            (<!
             (http/get (api-root "/todos")
                       {:with-credentials? false
                        :headers {"jimw-clj-token" (memoized-api-token)}
                        :query-params {:blog blog}}))]
        (let [body (:body response)]
          (op-fn body)))))

;; (create-todo "dasdsadsa" 12 2222 #(prn %))
(defn create-todo [text parid blog op-fn]
  (go (let [response
            (<!
             (http/post (api-root "/create-todo")
                        {:with-credentials? false
                         :headers {"jimw-clj-token" (memoized-api-token)}
                         :query-params {:content text :parid parid :blog blog}}))]
        (if (= (:status response) 200)
          (op-fn (:body response))
          (js/alert "Create todo failure!")))))

;; (update-todo 11 "aaadasdsadsaoooo" 12 2222 #(prn %))
(defn update-todo [id text #_parid blog done op-fn]
  (go (let [response
            (<!
             (http/put (str (api-root "/update-todo/") id)
                       {:with-credentials? false
                        :headers {"jimw-clj-token" (memoized-api-token)}
                        :query-params
                        (if (nil? done)
                          {:content text :blog blog}
                          {:content text #_:parid #_parid :blog blog :done done})}))]
        (if (= (:status response) 200)
          (op-fn (:body response))
          (js/alert "Update todo failure!")))))

(defn update-todo-parid [blog id parid op-fn]
  (go (let [response
            (<!
             (http/put (str (api-root "/update-todo/") id)
                       {:with-credentials? false
                        :headers {"jimw-clj-token" (memoized-api-token)}
                        :query-params
                        {:parid parid :blog blog}}))]
        (if (= (:status response) 200)
          (op-fn (:body response))
          (js/alert "Update todo failure!")))))

;; (delete-todo 11 #(prn %))
(defn delete-todo [id op-fn]
  (go (let [response
            (<!
             (http/delete (api-root "/delete-todo")
                          {:with-credentials? false
                           :headers {"jimw-clj-token" (memoized-api-token)}
                           :query-params {:id id}}))]
        (if (= (:status response) 200)
          (op-fn (:body response))
          (js/alert "Delete todo failure!")))))

(defn update-todo-sort [origins response target op-fn]
  (go (let [response
            (<!
             (http/post (api-root "/update-todo-sort")
                        {:with-credentials? false
                         :headers {"jimw-clj-token" (memoized-api-token)}
                         :json-params {:origins origins :response response :target target}}))]
        (if (= (:status response) 200)
          (op-fn (:body response))
          (js/alert "Update todo sort failure!")))))

;; (play-pcm-file "1519193567626" prn)
(defn play-pcm-file
  [time-id op-fn]
  (go (let [response
            (<!
             (http/post
              (str "http://" (get-pcm-ip) ":5557/cache" time-id "_xunfeiclj.pcm")
              {:with-credentials? false}))]
        #_(if (= (:status response) 200)
            (op-fn (:body response))
            (js/alert "Play pcm failure!")))))

;; TODOS: 移到公共的cljs上
(defn search-map-zh2en
  [q op-fn]
  (go (let [{:keys [status body]}
            (<!
             (http/get (api-root "/search-map-zh2en")
                       {:with-credentials? false
                        :headers {"jimw-clj-token" (memoized-api-token)}
                        :query-params {:q q}}))]
        (if (= status 200)
          (op-fn (:data body))
          (js/alert "Unauthorized !")))))

(defn todo-input [{:keys [content on-save on-stop items search-fn blog-id
                          focus-bdsug-blog-id search-wolframalpha-en editing]}]
  (let [val (r/atom content)
        stop #(do (reset! val "")
                  (if on-stop (on-stop)))
        save #(let [v (-> @val str clojure.string/trim)]
                (if-not (empty? v) (on-save v))
                (stop))]
    (fn [{:keys [id class placeholder]}]
      [:input {:type "text" :value @val
               :id id :class class :placeholder placeholder
               :on-blur (fn []
                          (reset! focus-bdsug-blog-id nil)
                          (if (> (count items) 9)
                            identity (save)))
               #_:on-blur #_(do (if (fn? search-fn)
                               (do
                                 (search-fn @val)
                                 (if (empty? @val) nil
                                     (do
                                       (record-event "search-todo" @val identity)
                                       )
                                     )
                                 )
                               (save))
                             (set! (.-display (.-style (. js/document (getElementById "bdsug-search")))) "none"))
               :on-focus
               #(do
                 (reset! focus-bdsug-blog-id blog-id)
                 )
               #_(let [bdsug-stat (->> (str "bdsug-search-" blog-id) getElementById (. js/document) .-style .-display)]
                            (if (= bdsug-stat "none")
                              (set! (.-display (.-style (. js/document (getElementById (str "bdsug-search-" blog-id))))) "block")
                              ))
               :on-change #(do
                             (let [valu (-> % .-target .-value)]
                               #_(if (fn? search-fn)
                                 (prn (search-fn valu)) nil)
                               #_(if search-text
                                   (reset! search-text valu))
                               (reset! val valu)
                               (if editing
                                 nil
                                 (search-map-zh2en
                                  valu
                                  (fn [data] (reset! search-wolframalpha-en data)))
                                 )
                               ;;(record-event "search-todo" valu identity)
                               )
                             )
               :on-key-down #(case (.-which %)
                               13
                               (if (> (count items) 9)
                                 (if (fn? search-fn)
                                   (do
                                     (search-fn @val)
                                     (if (empty? @val) nil
                                         (do
                                           (record-event "search-todo" @val identity)
                                           )
                                         )
                                     )
                                   (save))
                                 (save))
                               27 (stop)
                               nil)}])))

(defn todo-input-par [{:keys [id content on-save on-stop on-blur search-wolframalpha-en]}]
  (let [val (r/atom content)
        stop #(do (reset! val "")
                  (if on-stop (on-stop)))
        save #(let [v (-> @val str clojure.string/trim)]
                (if-not (empty? v) (on-save v))
                (stop))]
    (fn [{:keys [id class placeholder]}]
      [:input.input-par {:type "text" :value @val
                         :id id :class class :placeholder placeholder
                         :on-blur #(if on-blur (do (save) (on-blur)) (save))
                         :on-change #(do
                                       (reset! val (-> % .-target .-value))
                                       (search-map-zh2en
                                        @val
                                        (fn [data] (reset! search-wolframalpha-en data)))
                                       )
                         :on-key-down #(case (.-which %)
                                         13 (save)
                                         27 (stop)
                                         nil)}])))

(def todo-edit (with-meta todo-input
                 {:component-did-mount #(.focus (r/dom-node %))}))


(defn todo-stats [{:keys [filt active done]}]
  (let [props-for (fn [name]
                    {:class (if (= name @filt) "selected")
                     :on-click #(reset! filt name)})]
    [:div
     [:span#todo-count
      [:strong active] " " #_(case active 1 "item" "items") " left"]
     [:ul#filters
      [:li [:a (props-for :all) "All"]]
      [:li [:a (props-for :active) "Active"]]
      [:li [:a (props-for :done) "Completed"]]]
     #_(when (pos? done)
         [:button#clear-completed ;; {:on-click clear-done}
          "Clear completed " done])]))

(defn todo-stats-tmp [{:keys [filt active done]}]
  (let [props-for (fn [name]
                    {:class (if (= name @filt) "selected")
                     :on-click #(reset! filt name)})]
    [:div
     [:ul#filters
      [:li [:a (props-for :all) "A"]]
      [:li [:a (props-for :active) "O"]]
      [:li [:a (props-for :done) "C"]]]
     (when (pos? done)
       [:button#clear-completed ;; {:on-click clear-done}
        "Clear completed " done])]))

(declare search-match-fn)

(def new-todo-par
  (fn [sort_id blog-list blog-id on-blur search-text search-wolframalpha-en]
    [todo-input-par
     {:id sort_id
      :type "text"
      :search-wolframalpha-en search-wolframalpha-en
      :placeholder (str "Subneed to be done for " sort_id "?")
      :on-blur on-blur
      :on-save
      (fn [content]
        (create-todo
         content sort_id blog-id
         (fn [data]
           (swap! blog-list update-in
                  [(:blog data) :todos]
                  ;; New 出来的不接受搜索的原因是什么? => 很可能是for的问题
                  ;;1. new出来的如果默认为true,怎么搜索都在
                  ;;2. new出来的如果默认为false,怎么搜索都不在
                  ;;3. 除非刷新整个todo列表才起作用
                  #(assoc % (:sort_id data) {:id (:sort_id data) :sort_id (:id data)
                                             :search ;;false
                                             (search-match-fn content @search-text)
                                             :parid (:parid data) :content (:content data)})))))}]))

(defn get-todo-sort-id [id items]
  (->
   (filter
    (fn [x] x (= (last x) id)) items)
   first last))

(defn todo-parid-input [{:keys [parid-val on-blur on-save]}]
  [:input {:type "number"
           :value @parid-val
           :on-blur on-blur
           :on-change #(reset! parid-val (-> % .-target .-value))
           :on-key-down #(case (.-which %)
                           13 (on-save @parid-val)
                           27 (on-save @parid-val)
                           nil)}])

(defn todo-item []
  (let [editing (r/atom false)]
    (fn [{:keys [id done content sort_id parid file]} blog-list blog-id
         todo-target todo-begin origins search-text
         search-wolframalpha-en
         list-editing]
      (let [parid-val (r/atom "")
            _ (reset! parid-val parid)]
        [:li {:class (str (if done "completed ")
                          (if @editing "editing"))
              :draggable true
              :on-drag-start #(do (prn (str "开始拖动" sort_id))
                                  (reset! todo-begin sort_id))
              :on-drag-end (fn []
                             (do
                               (prn (str "目标位置" @todo-target))
                               (update-todo-sort
                                (vec origins)
                                @todo-begin
                                (get-todo-sort-id @todo-target (vec origins))
                                (fn [data]
                                  (count
                                   (str 
                                    (for [mdata data]
                                      (swap! blog-list update-in
                                             [blog-id :todos]
                                             #(assoc % (:sort_id mdata) {:id (:sort_id mdata)
                                                                         :sort_id (:id mdata)
                                                                         :parid (:parid mdata)
                                                                         :content (:content mdata)})))))))))
              ;; 一直打印出来: TODOS修改经过上方的颜色
              :on-drag-over #(reset! todo-target id)}
         [:div.view
          [:button {:class (str "copybutton" id "test")
                    :style {:display "none"}
                    :data-clipboard-text (str (:content (get (:todos (get @blog-list blog-id)) id)))}
           "copy"]
          [:input.toggle-checkbox
           {:type "checkbox"
            :checked done
            :on-change
            (fn []
              (let [done-stat (if (true? done) false true)
                    _ (let [clipboard (new js/ClipboardJS (str ".copybutton" id "test"))
                            _ (.info js/console "todocopy" id)]
                        (.on clipboard "success" (fn [e]
                                                   (.info js/console "Action:" (.-action e))
                                                   (.info js/console "Text:" (.-text e))
                                                   (.info js/console "Trigger:" (.-trigger e))
                                                   (.clearSelection e))))
                    _ (.click (last (array-seq
                                     (. js/document (getElementsByClassName  (str "copybutton" id "test") )))))
                    ]
                (swap! blog-list update-in
                       [blog-id :todos id :done] (fn [x] done-stat))
                #_(let [content (:content (get (:todos (get @blog-list blog-id)) id))]
                  (something/copyToClipboard content))
                (update-todo
                 sort_id nil blog-id done-stat
                 #(prn %))))}]
          [:label.todo-front-size {:on-click #(reset! editing true)}
           [:a {:on-click (fn [] (if (nil? file)
                                   (js/alert "pcm file is nil!")
                                   (play-pcm-file (re-find #"\d\d+" file) identity)
                                   ))} sort_id "◔"]  content]
          [:button.destroy {:on-click
                            (fn []
                              (if @list-editing
                                (delete-todo
                                 sort_id
                                 (fn [data]
                                   (swap! blog-list update-in
                                          [blog-id :todos] #(dissoc % id))))
                                (js/alert "todos 只读模式")
                                ))}]
          [:button.reply {:on-click #(set! (.-display (.-style (. js/document (getElementById (str "input-label-id-" id)))) ) "block")}]
          [:div.input-label {:id (str "input-label-id-" id)}
           (new-todo-par sort_id blog-list blog-id
                         #(set! (.-display (.-style (. js/document (getElementById (str "input-label-id-" id)))) ) "none")
                         search-text
                         search-wolframalpha-en)
           ;;
           (for [item #_[[1 11] [2 22] [3 33]]
                 @search-wolframalpha-en]
             [:ul
              [:li {:style {:font-size "1rem"}}
               (str (first item) ". " (last item))
               ]]
             )
           ;;
           ]
          [:button.button-parid {:on-click #(set! (.-display (.-style (. js/document (getElementById (str "input-parid-id-" id)))) ) "block")}]
          [:label.input-parid {:id (str "input-parid-id-" id)}
           [todo-parid-input
            {:parid-val parid-val
             :on-save #(update-todo-parid blog-id sort_id % (fn [] (set! (.-display (.-style (. js/document (getElementById (str "input-parid-id-" id)))) ) "none")))
             :on-blur #(set! (.-display (.-style (. js/document (getElementById (str "input-parid-id-" id)))) ) "none")}]]]
         (when @editing
           [todo-edit {:class "edit" :content content
                       :editing @editing
                       :on-save
                       (fn [content]
                         (update-todo
                          sort_id content blog-id nil
                          #(swap! blog-list update-in [blog-id :todos id :content] (fn [x] (:content %)))))
                       :on-stop #(reset! editing false)}])]))))

(defn new-todo [blog-list blog-id items parid-first-id search-fn focus-bdsug-blog-id
                search-wolframalpha-en]
  [todo-input {:id "new-todo"
               :blog-id blog-id
               :focus-bdsug-blog-id focus-bdsug-blog-id
               :search-wolframalpha-en search-wolframalpha-en
               :placeholder (if (> (count items) 9)
                              "Search todo"
                              "Add todo")
               :search-fn search-fn
               :items     items
               :on-save
               (fn [content]
                 (create-todo
                  content @parid-first-id blog-id
                  (fn [data]
                    (if (= @parid-first-id 1)
                      (reset! parid-first-id (:id data)))
                    (swap! blog-list update-in
                           [(:blog data) :todos]
                           #(assoc % (:sort_id data) {:id (:sort_id data) :sort_id (:id data)
                                                      :search true
                                                      :parid (:parid data) :content (:content data)}))
                    (swap! @blog-list update-in [37581 :todos] (fn [x] (sorted-map) ))
                    )))}])

(defn aaaa
  [items filt blog-list blog-id
   todo-target todo-begin origins search-text
   done active
   search-wolframalpha-en
   list-editing]
    (when (-> items count pos?)
      [:div
       [:section#main
        [:ul#todo-list.todo-list-class
         (for [todo
               (filter
                (fn [item]
                  (= (:search item) true)
                  #_(if (empty? @search-text)
                      true
                      (every?
                       true?
                       (map
                        (fn [x]
                          (if (re-matches (re-pattern (str "(.*)" x "(.*)")) (str item)) true false))
                        (str/split @search-text " ")))))
                (filter
                 (fn [item] (not (re-matches #"\d" (:content item))))
                 (filter
                  (case @filt
                    :active (complement :done)
                    :done :done
                    :all identity)
                  ;;
                  #_(do
                      (take (count items)
                            (for [todo items]
                              (prn todo)
                              ))
                      items
                      )
                  ;;(get-in @blog-list [37581 :todos])
                  items
                  ;;
                  )))]
           ^{:key (:id todo)} [todo-item todo blog-list blog-id
                               todo-target todo-begin origins search-text
                               search-wolframalpha-en
                               list-editing
                               ])]]
       [:footer#footer
        [todo-stats {:active active :done done :filt filt}]]]))

;; (search-match-fn "完成websocket某某功能" "完成 功能") ;; => true
;; (search-match-fn "完成websocket某某功能" "完成 功能 aaa") ;; => false
(defn search-match-fn [item search-text]
  (if (empty? search-text)
    true
    (every?
     true?
     (map
      (fn [x]
        (if (re-matches (re-pattern (str "(.*)" x "(.*)")) item) true false))
      (str/split search-text " ")))))

(defn todo-app [blog-list blog-id search-wolframalpha-en focus-bdsug-blog-id]
  (let [filt (r/atom :all)
        editing (r/atom false)
        search-text (r/atom "")
        ]
    (fn []
      (let [items (vals (get-in @blog-list [blog-id :todos]))
            parid-first-id (-> (if (= (count items) 0) 1
                                   (->
                                    (filter #(= (:parid %) 1) items)
                                    first :sort_id)) r/atom)
            done (->> items (filter :done) count)
            active (- (count items) done)
            todo-target (atom 0)
            todo-begin (atom 0)
            origins (map #(vector (:sort_id %) (:id %)) items)
            set-search-fn (fn [id true-or-false]
                            (swap! blog-list update-in
                                   [blog-id :todos id :search] (fn [x] true-or-false)))
            search-fn #(do
                         (reset! search-text %)
                         ;;
                         (prn (str "========" (count (vals (get-in @blog-list [blog-id :todos])))))
                         (->
                          (for [{:keys [content id] :as todo} (vals (get-in @blog-list [blog-id :todos]))]
                            (do
                              (set-search-fn id
                                             (if (empty? @search-text)
                                               true
                                               (search-match-fn content @search-text)
                                               )
                                             )
                              1)
                            ) str prn)
                         #_(prn "AAAAAAAA")
                         ;;
                         @search-text)]
        [:div
         #_[todo-stats-tmp {:active active :done done :filt filt}]
         #_[:br]
         [:section#todoapp
          [:header#header
           [:button.btn.margin-download
            {:on-click #(if @editing
                          (reset! editing false)
                          (reset! editing true))}
            (if @editing
              "Edit mode"
              "Read mode")]
           (new-todo blog-list blog-id items parid-first-id search-fn focus-bdsug-blog-id
                     search-wolframalpha-en)]
          [:div {:class "bdsug" :id (str "bdsug-search-" blog-id)}
           (if (= @focus-bdsug-blog-id blog-id)
             (for [item @search-wolframalpha-en]
             [:ul
              [:li #_{:on-click #(do (reset! append-stri (str (last item)))
                                     (set! (.-value alpha-input) (str  (.-value alpha-input) " "  (str (last item))))
                                     (set! (.-value google-input) (str  (.-value google-input) " "  (str (last item))))
                                     )}
               (str (first item) ". " (last item))
               ]]
             ))
           
           ]
          ;;;;;;;;
          (aaaa (vals (get-in @blog-list [blog-id :todos])) filt blog-list blog-id
                todo-target todo-begin origins search-text
                done active
                search-wolframalpha-en
                editing)
          ;;;;;;;
          ]
         #_[:footer#info
            [:p "Double-click to edit a todo"]]]))))

