(ns jimw-clj.edit
  (:require [reagent.core :as r]))

(defn blog-name-input-par [{:keys [id name on-save on-stop]}]
  (let [val (r/atom name)
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

(def blog-name-edit (with-meta blog-name-input-par
                      {:component-did-mount #(.focus (r/dom-node %))}))

(defn blog-name-item []
  (let [editing (r/atom false)]
    (fn [{:keys [id name save-fn]}]
      [:li.liststyle {:class (str (if @editing "editing"))}
       [:div.view
        [:h3
         [:label.blog-name {:on-double-click #(reset! editing true)} name]]]
       (when @editing
         [blog-name-edit {:class "edit"
                          :on-save  #(save-fn id %)
                          :name name
                          :on-stop #(reset! editing false)}])])))
