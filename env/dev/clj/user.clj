(ns user
  (:require [mount.core :as mount]
            [jimw-clj.figwheel :refer [start-fw stop-fw cljs]]
            jimw-clj.core))

(defn start []
  (mount/start-without #'jimw-clj.core/repl-server))

(defn stop []
  (mount/stop-except #'jimw-clj.core/repl-server))

(defn restart []
  (stop)
  (start))


