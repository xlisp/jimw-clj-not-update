(ns jimw-clj.events
  (:require-macros [cljs.core.async.macros :as a])
  (:require [cljs.core.async :as a]
            [jimw-clj.sente :as sente]
            [jimw-clj.db :as db]
            [re-frame.core :as re-frame]))

;;
;; Sente-related
;;

(defn dispatch-sente-event-msg
  [{:keys [id] :as ev-msg}]
  (re-frame/dispatch [id ev-msg]))

(re-frame/reg-fx
 :sente/connection
 (fn [_]
   (sente/connect! dispatch-sente-event-msg)))

(re-frame/reg-fx
 :sente/event
 (fn [{:keys [event dispatch-to]}]
   (if dispatch-to
     (sente/send! event #(re-frame/dispatch (conj dispatch-to %1)))
     (sente/send! event))))

(re-frame/reg-event-fx
 :sente/connect
 (fn [_ _]
   {:sente/connection true}))

(re-frame/reg-event-db
 :chsk/state
 (fn [db [_ ev-msg]]
   (let [[id [_ new-state]] (:event ev-msg)]
     (assoc db :sente new-state))))

(re-frame/reg-event-fx
 :chsk/handshake
 (fn [_ _]
   {:dispatch [:msg/fetch-all]}))

(re-frame/reg-event-fx
 :chsk/recv
 (fn [_ [_ ev-msg]]
   {:dispatch (:?data ev-msg)}))

(re-frame/reg-event-fx
 :chsk/ws-ping
 (fn [_ _]
   ;; Do nothing on ping.
   ))

;;
;; App-specific
;;

(re-frame/reg-event-db
 :db/initialize
 (fn [db _]
   (merge db/default-value db)))

(re-frame/reg-event-fx
 :msg/fetch-all
 (fn [_ [_ message]]
   {:sente/event {:event [:msg/fetch-all]
                  :dispatch-to [:msg/handle-response]}}))

(re-frame/reg-event-db
 :msg/handle-response
 (fn [db [_ {:keys [msgs]}]]
   (assoc db :msgs msgs)))

(re-frame/reg-event-db
 :msg/update-new
 (fn [db [_ {:keys [new-msg]}]]
   (assoc db :new-msg new-msg)))

(re-frame/reg-event-fx
 :msg/create
 (fn [{:keys [db]} _]
   {:sente/event {:event [:msg/create (select-keys db [:new-msg])]}
    :db (dissoc db :new-msg)}))

(re-frame/reg-event-db
 :msg/push-all
 (fn [db [_ {:keys [msgs]}]]
   (assoc db :msgs msgs)))
