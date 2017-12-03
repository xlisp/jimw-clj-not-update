(ns jimw-clj.todos
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [reagent.core :as r :refer [atom]]
            [cljs-http.client :as http]
            [cljs.core.async :refer [<!]]
            [cemerick.url :refer (url url-encode)]
            #_[cljsjs.jquery]))

(defn api-root [url] (str (-> js/window .-location .-origin) url))

(defn get-api-token
  []
  (->
   (.getItem js/localStorage "[\"~#'\",\"~:api-token\"]")
   (clojure.string/split "\"")
   (get 3)))

(def memoized-api-token (memoize get-api-token))

(defn tree-todo-generate [blog]
  (go (let [response
            (<!
             (http/post (api-root "/tree-todo-generate")
                        {:with-credentials? false
                         :headers {"jimw-clj-token" (memoized-api-token)}
                         :query-params {:blog blog}}))])))

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

(defn todo-input [{:keys [content on-save on-stop]}]
  (let [val (r/atom content)
        stop #(do (reset! val "")
                  (if on-stop (on-stop)))
        save #(let [v (-> @val str clojure.string/trim)]
                (if-not (empty? v) (on-save v))
                (stop))]
    (fn [{:keys [id class placeholder]}]
      [:input {:type "text" :value @val
               :id id :class class :placeholder placeholder
               :on-blur save
               :on-change #(reset! val (-> % .-target .-value))
               :on-key-down #(case (.-which %)
                               13 (save)
                               27 (stop)
                               nil)}])))

(defn todo-input-par [{:keys [id content on-save on-stop on-blur]}]
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
                         :on-change #(reset! val (-> % .-target .-value))
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

(def new-todo-par
  (fn [sort_id blog-list blog-id on-blur]
    [todo-input-par
     {:id sort_id
      :type "text"
      :placeholder (str "Subneed to be done for " sort_id "?")
      :on-blur on-blur
      :on-save
      (fn [content]
        (create-todo
         content sort_id blog-id
         (fn [data]
           (swap! blog-list update-in
                  [(:blog data) :todos]
                  #(assoc % (:sort_id data) {:id (:sort_id data) :sort_id (:id data)
                                             :parid (:parid data) :content (:content data)})))))}]))

(defn get-todo-sort-id [id items]
  (->
   (filter
    (fn [x] x (= (last x) id)) items)
   first last))

(defn todo-item []
  (let [editing (r/atom false)]
    (fn [{:keys [id done content sort_id]} blog-list blog-id
         todo-target todo-begin origins]
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
        [:input.toggle-checkbox
         {:type "checkbox"
          :checked done
          :on-change
          (fn []
            (let [done-stat (if (true? done) false true)]
              (swap! blog-list update-in
                     [blog-id :todos id :done] (fn [x] done-stat))
              (update-todo
               sort_id nil blog-id done-stat
               #(prn %))))}]
        [:label.todo-front-size {:on-double-click #(reset! editing true)} content]
        [:button.destroy {:on-click
                          (fn []
                            (delete-todo
                             id
                             (fn [data]
                               (swap! blog-list update-in
                                      [blog-id :todos] #(dissoc % id)))))}]
        [:button.reply {:on-click #(set! (.-display (.-style (. js/document (getElementById (str "input-label-id-" id)))) ) "block")}]
        [:div.input-label {:id (str "input-label-id-" id)}
         (new-todo-par sort_id blog-list blog-id
                       #(set! (.-display (.-style (. js/document (getElementById (str "input-label-id-" id)))) ) "none"))]]
       (when @editing
         [todo-edit {:class "edit" :content content
                     :on-save
                     (fn [content]
                       (update-todo
                        sort_id content blog-id nil
                        #(swap! blog-list update-in [blog-id :todos id :content] (fn [x] (:content %)))))
                     :on-stop #(reset! editing false)}])])))

(defn new-todo [blog-list blog-id items parid-first-id]
  [todo-input {:id "new-todo"
               :placeholder "What needs to be done?"
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
                                                      :parid (:parid data) :content (:content data)})))))}])

(defn todo-app [blog-list blog-id]
  (let [filt (r/atom :all)]
    (fn []
      (let [items (vals (get-in @blog-list [blog-id :todos]))
            parid-first-id (-> (if (= (count items) 0) 1
                                   (->
                                    (filter #(= (:parid %) 1) items)
                                    first :id)) r/atom)
            done (->> items (filter :done) count)
            active (- (count items) done)
            todo-target (atom 0)
            todo-begin (atom 0)
            origins (map #(vector (:sort_id %) (:id %)) items)]
        [:div
         #_[todo-stats-tmp {:active active :done done :filt filt}]
         #_[:br]
         [:button.btn.tree-btn
          {:on-click
           #(do (js/alert "Update...")
                (tree-todo-generate blog-id)) } "Generate"]
         [:a.btn.margin-download
          {:href (str "/todos-" blog-id ".gv")
           :download (str "past_" blog-id "_navs.zip")} "Download"]
         [:section#todoapp
          [:header#header
           (new-todo blog-list blog-id items parid-first-id)]
          (when (-> items count pos?)
            [:div
             [:section#main
              [:ul#todo-list
               (for [todo (filter
                           (case @filt
                             :active (complement :done)
                             :done :done
                             :all identity) items)]
                 ^{:key (:id todo)} [todo-item todo blog-list blog-id
                                     todo-target todo-begin origins])]]
             [:footer#footer
              [todo-stats {:active active :done done :filt filt}]]])]
         #_[:footer#info
            [:p "Double-click to edit a todo"]]]))))
