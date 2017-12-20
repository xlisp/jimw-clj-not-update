(ns jimw-clj.sente
  (:require [mount.core :as mount]
            [taoensso.sente :as sente]
            [taoensso.sente.server-adapters.immutant :as sente-immutant]))

(defmulti event-msg-handler :id)

(defmethod event-msg-handler :default
  [{:keys [id event] :as ev-msg}]
  (if-not (contains? #{:chsk/uidport-open :chsk/uidport-close :chsk/ws-ping} id)
    (println (str "No handler for Sente event " id))))

(mount/defstate sente
  :start
  (let [{:keys [ch-recv send-fn connected-uids
                ajax-post-fn ajax-get-or-ws-handshake-fn]}
        (sente/make-channel-socket! (sente-immutant/get-sch-adapter)
                                    {:user-id-fn
                                     (fn [_]
                                       (str (java.util.UUID/randomUUID)))})
        router (sente/start-chsk-router! ch-recv event-msg-handler)]
    (println "Starting Sente")
    {:ch-recv ch-recv
     :send-fn send-fn
     :ajax-post-fn ajax-post-fn
     :ajax-get-or-ws-handshake-fn ajax-get-or-ws-handshake-fn
     :connected-uids connected-uids
     :router router})
  :stop
  (when-let [stop-router (:router sente)]
    (println "Stopping Sente")
    (assoc sente :router (stop-router))))
