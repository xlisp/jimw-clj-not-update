(ns jimw-clj.config
  (:require [cprop.core :refer [load-config]]
            [cprop.source :as source]
            [mount.core :refer [args defstate]]))

(def jimw-conf (atom {}))

(defn jimw-load-conf []
  (reset! jimw-conf (load-file "config/config.clj")))

(defstate env :start (load-config
                       :merge
                       [(args)
                        (source/from-system-props)
                        (source/from-env)]))
