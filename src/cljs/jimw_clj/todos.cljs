(ns jimw-clj.todos
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [reagent.core :as r :refer [atom]]
            [cljs-http.client :as http]
            [cljs.core.async :refer [<!]]
            [cemerick.url :refer (url url-encode)]))

(defn api-root [url] (str (-> js/window .-location .-origin) url))
(defonce todos (r/atom (sorted-map)))

;; (get-todos-list 4857 #(-> (zipmap  (map :id %) %) prn))
(defn get-todos-list
  [blog op-fn]
  (go (let [response
            (<!
             (http/get (api-root "/todos")
                       {:with-credentials? false
                        :query-params {:blog blog}}))]
        (let [body (:body response)]
          (op-fn body)))))

;; (create-todo "dasdsadsa" 12 2222 #(prn %))
(defn create-todo [text parid blog op-fn]
  (go (let [response
            (<!
             (http/post (api-root "/create-todo")
                        {:with-credentials? false
                         :query-params {:content text :parid parid :blog blog}}))]
        (if (= (:status response) 200)
          (op-fn (:body response))
          (js/alert "Create todo failure!")))))

;; (update-todo 11 "aaadasdsadsaoooo" 12 2222 #(prn %))
(defn update-todo [id text #_parid blog op-fn]
  (go (let [response
            (<!
             (http/put (str (api-root "/update-todo/") id)
                       {:with-credentials? false
                        :query-params {:content text #_:parid #_parid :blog blog}}))]
        (if (= (:status response) 200)
          (op-fn (:body response))
          (js/alert "Update todo failure!")))))

;; (delete-todo 11 #(prn %))
(defn delete-todo [id op-fn]
  (go (let [response
            (<!
             (http/delete (api-root "/delete-todo")
                          {:with-credentials? false
                           :query-params {:id id}}))]
        (if (= (:status response) 200)
          (op-fn (:body response))
          (js/alert "Delete todo failure!")))))

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

(defn todo-input-par [{:keys [id content on-save on-stop]}]
  (let [val (r/atom content)
        stop #(do (reset! val "")
                  (if on-stop (on-stop)))
        save #(let [v (-> @val str clojure.string/trim)]
                (if-not (empty? v) (on-save v))
                (stop))]
    (fn [{:keys [id class placeholder]}]
      [:input.input-par {:type "text" :value @val
                         :id id :class class :placeholder placeholder
                         :on-blur save
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
      [:strong active] " " (case active 1 "item" "items") " left"]
     [:ul#filters
      [:li [:a (props-for :all) "All"]]
      [:li [:a (props-for :active) "Active"]]
      [:li [:a (props-for :done) "Completed"]]]
     (when (pos? done)
       [:button#clear-completed ;; {:on-click clear-done}
        "Clear completed " done])]))

(def new-todo-par
  (fn [id]
    [todo-input-par
     {:id id
      :type "text"
      :placeholder (str "Subneed to be done for " id "?")
      ;; :on-save #(add-todo % id 4857)
      }]
    )
  )

(defn todo-item []
  (let [editing (r/atom false)]
    (fn [{:keys [id done content]} blog-list blog-id]
      [:li {:class (str (if done "completed ")
                        (if @editing "editing"))}
       [:div.view
        [:label {:on-double-click #(reset! editing true)} content]
        [:button.destroy {:on-click
                          (fn []
                            (delete-todo
                             id
                             (fn [data]
                               (swap! blog-list update-in
                                      [blog-id :todos] #(dissoc % id)))))}]
        [:button.reply {:on-click #(set! (.-display (.-style (. js/document (getElementById (str "input-label-id-" id)))) ) "block") }]
        [:label.input-label { :id (str "input-label-id-" id)} (new-todo-par id)]]
       (when @editing
         [todo-edit {:class "edit" :content content
                     :on-save
                     (fn [content]
                       (update-todo
                        id content blog-id
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
                           #(assoc % (:id data) {:id (:id data) :parid (:parid data) :content (:content data)})))))}])

(defn todo-app [blog-list blog-id]
  (let [filt (r/atom :all)]
    (fn []
      (let [items (vals (get-in @blog-list [blog-id :todos]))
            parid-first-id (-> (if (= (count items) 0) 1
                                   (->
                                    (filter #(= (:parid %) 1) items)
                                    first :id)) r/atom)
            done (->> items (filter :done) count)
            active (- (count items) done)]
        [:div
         [:section#todoapp
          [:header#header
           [:h1 "todos tree"]
           (new-todo blog-list blog-id items parid-first-id)]
          (when (-> items count pos?)
            [:div
             [:section#main
              [:ul#todo-list
               (for [todo items #_(filter (case @filt
                                            :active (complement :done)
                                            :done :done
                                            :all identity) items)]
                 ^{:key (:id todo)} [todo-item todo blog-list blog-id])]]
             [:footer#footer
              [todo-stats {:active active :done done :filt filt}]]])]
         [:footer#info
          [:p "Double-click to edit a todo"]]]))))
