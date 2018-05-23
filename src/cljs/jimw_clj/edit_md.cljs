(ns jimw-clj.edit-md
  (:require [reagent.core :as r]
            [myexterns.autosize :as autosize]
            [cljsjs.marked]))

(defn blog-content-input-par [{:keys [id name on-save on-stop]}]
  (let [val (r/atom name)
        stop #(do ;;(reset! val "")
                  (if on-stop (on-stop)))
        save #(let [v (-> @val str clojure.string/trim)]
                (if-not (empty? v) (on-save v))
                (stop))]
    (fn [{:keys [id class placeholder]}]
      [:textarea.auto-height {:type "text" :value @val
                         :id id :class class :placeholder placeholder
                         :on-blur save
                         :on-change #(reset! val (-> % .-target .-value))
                         :on-key-down #(case (.-which %)
                                         27 (stop)
                                         nil)}])))

(def blog-content-edit (with-meta blog-content-input-par
                         {:component-did-mount #(.focus (r/dom-node %))}))

(defn blog-content-item []
  (let [editing (r/atom false)]
    (fn [{:keys [id name save-fn]}]
      [:li.liststyle {:class (str (if @editing "editing"))}
       [:button.btn.margin-download
        {:on-click #(do
                      (if @editing
                        (do ;;(js/alert "保存博客")
                          (reset! editing false))
                        (do (reset! editing true)
                            (js/setTimeout (fn []
                                             (js/autosize (.querySelector js/document "textarea"))) 500)))
                      )}
        (if @editing
          "Save Blog"
          "Edit Blog")]
       [:p]
       [:div.view
        [:div {;;:on-double-click
               #_(do (reset! editing true)
                     (js/setTimeout (fn []
                                      (js/autosize (.querySelector js/document "textarea"))) 500))
               :dangerouslySetInnerHTML
               {:__html (js/marked name)}}]]
       (when @editing
         [blog-content-edit {:class "edit"
                             :on-save  #(save-fn id %)
                             :name name
                             ;;:on-stop #(reset! editing false)
                             }])])))
