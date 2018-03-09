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

#_(re-frame/reg-event-db
 :msg/push-all
 (fn [db [_ {:keys [msgs]}]]
   (assoc db :msgs msgs)))

;; (json-parse "{\"data\": [1, 2, 3]}") ;;=> {:data [1 2 3]}
(defn json-parse
  [json]
  (->
   (.parse js/JSON json)
   (js->clj :keywordize-keys true)))

;; (prn (json-parse @aamsgs))
(def json-parse-msgs-eg
  {:change [{:kind "update", :schema "public", :table "todos", :columnnames ["id" "blog" "parid" "content" "done" "sort_id" "created_at" "updated_at" "app_id" "file" "islast" "percent" "begin" "mend" "wctags" "origin_content"], :columntypes ["bigint" "bigint" "bigint" "text" "boolean" "integer" "timestamp with time zone" "timestamp with time zone" "integer" "text" "boolean" "integer" "integer" "integer" "jsonb" "text"], :columnvalues [279 40546 259 "aaaaaaakoiaa" false 238 "2018-03-09 10:30:57.715457+08" "2018-03-09 11:56:15.036576+08" nil nil nil nil nil nil "{}" nil], :oldkeys {:keynames ["id"], :keytypes ["bigint"], :keyvalues [279]}}]})
