(ns jimw-clj.views
  (:require [jimwclj.localization :refer [tr]]
            [re-frame.core :as re-frame]))

(defn msg-item
  [msg]
  [:div.msg-item msg])

(defn msg-list
  []
  (let [msgs (re-frame/subscribe [:msg/all])]
    (if-not (empty? @msgs)
      [:div.msg-list
       (for [[idx msg] (map-indexed vector @msgs)]
         ^{:key idx}
         [msg-item msg])])))

(defn new-msg-input
  []
  (let [new-msg (re-frame/subscribe [:msg/new])]
    [:div.new-msg
     [:input.new-msg__input
      {:placeholder (tr :new-msg)
       :auto-focus true
       :value @new-msg
       :on-change (fn [e]
                    (let [value (-> e .-target .-value)]
                      (re-frame/dispatch [:msg/update-new {:new-msg value}])))
       :on-key-press (fn [e]
                       (if (= 13 (.-charCode e))
                         (re-frame/dispatch [:msg/create])))}]]))

(defn main-view
  []
  [:div.msg-container
   #_[:h1.msg-title (tr :tiko-msg)]
   [new-msg-input]
   [msg-list]])
