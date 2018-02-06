(ns jimw-clj.api
  (:require
   [cheshire.core :as cjson]
   [compojure.core :refer :all]
   [compojure.route :as route]
   [clojure.tools.logging :as log]
   [clj-time.core :as t]
   [clj-time.local :as l]
   [clj-time.coerce :as ct]
   [clj-time.format :as f]
   [clj-time.coerce :as time-coerce]
   [jimw-clj.db.core :as db]
   [ring.util.http-response :refer :all]
   [ring.util.response :refer [response]]
   [buddy.sign.jwt :as jwt]
   [buddy.hashers :as hashers]
   [clj-time.core :as time]
   [taoensso.timbre :refer [error debug info]]
   [jimw-clj.config :as config]
   [clojure.java.io :as io]
   [clj-jri.R :as R]
   [jimw-clj.sente :as sente])
  (:import (org.apache.commons.codec.binary Base64)
           (org.apache.commons.io IOUtils))
  (:gen-class))

(defn sente-handler
  [req]
  (if (= (:uri req) "/chsk")
    (try
      (case (:request-method req)
        :get ((:ajax-get-or-ws-handshake-fn sente/sente) req)
        :post ((:ajax-post-fn sente/sente) req))
      (catch Exception e
        {:status 400}))))

(defn token-sign
  [ids]
  (jwt/sign
   (merge
    ids {:exp (time/plus (time/now) (time/millis 86400000))})
   (:jimw-clj-jwt-key @config/jimw-conf)))

(defn- check-password
  [text digest]
  (when (and text digest)
    (hashers/check text digest)))

(defn login
  [{:keys [params]}]
  (try
    (let [{:keys [id password username]}
          (db/get-user-by-username
           {:db db/conn :username (:username params)})]
      (if (check-password (:password params) password)
        (ok {:id id
             :username username
             :token (token-sign {:user username})})
        (unauthorized)))
    (catch Exception ex
      (unauthorized))))

(defn check-api-token
  [f]
  (fn [request]
    (if (get-in request [:params :jimw_clj_userinfo :user])
      (f request)
      (unauthorized))))

(defn get-blogs
  [{{:keys [q limit offset]
     :or   {limit 10 offset 0 q ""}} :params}]
  (ok (db/search-blogs {:db db/conn :q q :limit (Integer/parseInt limit) :offset (Integer/parseInt offset)})))

(defn update-blog
  [{{:keys [id name content]} :params}]
  (let [res (db/update-blog {:db db/conn :id (Integer/parseInt id) :name name :content content})]
    (if res
      (ok res) (not-found))))

(defn create-blog
  [{{:keys [name content]} :params}]
  (let [res (db/create-blog {:db db/conn :name name :content content})]
    (ok res)))

(defn get-todos
  [{{:keys [q blog]
     :or   {q ""}} :params}]
  (ok (db/search-todos {:db db/conn :q q :blog (Integer/parseInt blog)})))

(defn update-todo
  [{{:keys [id parid blog content done]} :params}]
  (let [res (db/update-todo
             (merge
              {:db db/conn
               :id (Integer/parseInt id)
               :blog (Integer/parseInt blog)
               :done done
               :content content}
              (if (seq parid)
                {:parid (Integer/parseInt parid)} {})))]
    (if res
      (ok res) (not-found))))

(defn create-todo
  [{{:keys [parid blog content]} :params}]
  (let [res (db/create-todo {:db db/conn
                             :parid (Integer/parseInt parid)
                             :blog (Integer/parseInt blog)
                             :content content})]
    (ok res)))

(defn create-todo-app
  [{{:keys [parid blog content
            app_id file islast percent begin end]} :params}]
  (let [res (db/create-todo-app
             {:db db/conn
              :parid (Integer/parseInt parid)
              :blog (Integer/parseInt blog)
              :content content
              :app_id  (Integer/parseInt app_id)
              :file    file    
              :islast  (Boolean/valueOf islast)
              :percent (Integer/parseInt percent)
              :begin   (Integer/parseInt begin)
              :end     (Integer/parseInt end)})]
    (ok res)))

(defn delete-todo
  [{{:keys [id]} :params}]
  (let [res (db/delete-todo {:db db/conn
                             :id (Integer/parseInt id)})]
    (if res
      (ok res) (not-found))))

;; headers => jimw-clj-token: "token" => {:params {:jimw_clj_userinfo {:user "abc"}}}
(defn test-api
  [{:keys [params]}]
  (ok {:params params :rtest (R/eval "rnorm(5)") }))

;; (db/tree-todo-generate {:db db/conn :blog 4859})
;; (db/writer-tree-file 4859)
(defn tree-todo-generate
  [{{:keys [blog]} :params}]
  (do
    (info (str "======>> tree-todo-generate"
               (db/tree-todo-generate
                {:db db/conn
                 :blog (Integer/parseInt blog)})))
    (Thread/sleep 1000)
    (db/writer-tree-file (Integer/parseInt blog))
    (ok {:msg "ok!"})))

(defn tree-todo-generate-new
  [{{:keys [blog]} :params}]
  (ok {:data
       (db/tree-todo-generate-new
        {:db db/conn
         :blog (Integer/parseInt blog)})}))

(defn record-event
  [{{:keys [event_name info event_data]} :params}]
  (ok (db/insert-event {:db db/conn
                        :event_name event_name
                        :info       info
                        :event_data event_data})))

(defn update-todo-sort
  [{{:keys [origins response target]} :params}]
  (ok (db/update-todo-sort {:db db/conn
                            :origins (into {} origins)
                            :response response
                            :target target})))

(defn file-to-base64-string
  [file-name]
  (let [string-to-base64
        (fn [st]
          (String. (Base64/encodeBase64 st)))]
    (str "data:image/png;base64," (string-to-base64  (IOUtils/toByteArray (io/input-stream file-name))))))

(defn get-blog-wctags
  [{{:keys [id]} :params}]
  (let [res (db/get-blog-wctags {:db db/conn :id (Integer/parseInt id)})]
    (if res (ok {:data (sort-by (fn [item] (* (last item) -1)) (:wctags res))})
        (not-found))))

(defn search-sqldots
  [{{:keys [q]} :params}]
  (ok
   {:data
    (str "digraph g { graph [ rankdir = \"LR\" ]; \n"
         (clojure.string/join
          "\n"
          (map :content
               (db/search-sqldots
                {:db db/conn :q
                 (if (seq q)
                   (clojure.string/replace q #"-" "_")
                   "")})))
         "\n}")}))

(defn search-mapen
  [{{:keys [q]} :params}]
  (ok
   {:data
    (into
     {}
     (map-indexed
      vector
      (clojure.string/split (db/map->en db/conn q) #" ")))}))

(defroutes api-routes
  (POST "/login" [] login)
  (GET  "/chsk" req (sente-handler req))
  (GET "/test-api" [] (check-api-token test-api))
  (GET "/blogs" [] (check-api-token get-blogs))
  (PUT "/update-blog/:id" [] (check-api-token update-blog))
  (POST "/create-blog" [] (check-api-token create-blog))
  (GET "/todos" [] (check-api-token get-todos))
  (PUT "/update-todo/:id" [] (check-api-token update-todo))
  (POST "/create-todo" [] (check-api-token create-todo))
  (POST "/create-todo-app" [] (check-api-token create-todo-app))
  (DELETE "/delete-todo" [] (check-api-token delete-todo))
  (POST "/tree-todo-generate" [] (check-api-token tree-todo-generate))
  (POST  "/tree-todo-generate-new" [] (check-api-token tree-todo-generate-new))
  (POST "/record-event" [] (check-api-token record-event))
  (POST "/update-todo-sort" [] (check-api-token update-todo-sort))
  (GET "/get-blog-wctags" [] (check-api-token get-blog-wctags))
  (GET "/search-sqldots" [] (check-api-token search-sqldots))
  (GET "/search-mapen" [] (check-api-token search-mapen)))
