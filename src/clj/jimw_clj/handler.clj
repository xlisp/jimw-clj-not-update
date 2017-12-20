(ns jimw-clj.handler
  (:require
   [compojure.core :refer [routes wrap-routes]]
   [jimw-clj.layout :refer [error-page]]
   [jimw-clj.routes.home :refer [home-routes]]
   [compojure.route :as route]
   [jimw-clj.env :refer [defaults]]
   [mount.core :as mount]
   [jimw-clj.middleware :as middleware]
   [jimw-clj.api :as api]
   [jimw-clj.msg]))

(mount/defstate init-app
  :start ((or (:init defaults) identity))
  :stop  ((or (:stop defaults) identity)))

(def app-routes
  (routes
   (-> #'home-routes
       (wrap-routes middleware/wrap-csrf)
       (wrap-routes middleware/wrap-formats))
   (-> #'api/api-routes
       ;; (wrap-routes middleware/wrap-csrf)
       (wrap-routes middleware/wrap-formats))
   (route/not-found
    (:body
     (error-page {:status 404
                  :title "page not found"})))))

(defn app [] (middleware/wrap-base #'app-routes))
