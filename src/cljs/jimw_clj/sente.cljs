(ns jimw-clj.sente
  (:require-macros [cljs.core.async.macros :as a])
  (:require [cljs.core.async :as a]
            [taoensso.sente :as sente]))

(defonce state (atom nil))

(defn connect!
  [event-msg-handler]
  (let [channel-socket (sente/make-channel-socket! "/chsk" {:type :auto})
        router (sente/start-client-chsk-router! (:ch-recv channel-socket)
                                                event-msg-handler)]
    (reset! state {:channel-socket channel-socket
                   :router router})))

(defn send!
  ([event]
   (send! event nil))
  ([event cb]
   (if-let [send-fn (get-in @state [:channel-socket :send-fn])]
     (send-fn event 5000 cb)
     (js/console.warn "Sente not connected to server"))))
