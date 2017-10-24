(ns jimw-clj.env
  (:require [clojure.tools.logging :as log]))

(def defaults
  {:init
   (fn []
     (log/info "\n-=[jimw-clj started successfully]=-"))
   :stop
   (fn []
     (log/info "\n-=[jimw-clj has shut down successfully]=-"))
   :middleware identity})
