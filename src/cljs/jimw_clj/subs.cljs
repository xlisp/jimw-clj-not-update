(ns jimw-clj.subs
  (:require [re-frame.core :as re-frame]))

(re-frame/reg-sub
 :msg/all
 (fn [db _]
   (:msgs db)))

(re-frame/reg-sub
 :msg/new
 (fn [db _]
   (:new-msg db)))
