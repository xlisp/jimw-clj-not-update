(ns jimw-clj.env
  (:require [selmer.parser :as parser]
            [clojure.tools.logging :as log]
            [jimw-clj.dev-middleware :refer [wrap-dev]]))

(def defaults
  {:init
   (fn []
     (parser/cache-off!)
     (log/info "\n-=[jimw-clj started successfully using the development profile]=-"))
   :stop
   (fn []
     (log/info "\n-=[jimw-clj has shut down successfully]=-"))
   :middleware wrap-dev})
