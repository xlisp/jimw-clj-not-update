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
   [jimw-clj.config :as config])
  (:gen-class))

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
  (let [res (db/update-todo {:db db/conn
                             :id (Integer/parseInt id)
                             ;; :parid (Integer/parseInt parid)
                             :blog (Integer/parseInt blog)
                             :done done
                             :content content})]
    (if res
      (ok res) (not-found))))

(defn create-todo
  [{{:keys [parid blog content]} :params}]
  (let [res (db/create-todo {:db db/conn
                             :parid (Integer/parseInt parid)
                             :blog (Integer/parseInt blog)
                             :content content})]
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
  (ok {:params params}))

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

(defn record-event
  [{{:keys [event_name info event_data]} :params}]
  (ok (db/insert-event {:db db/conn
                        :event_name event_name
                        :info       info
                        :event_data event_data})))

(defn update-todo-sort
  [{{:keys [origins response target]} :params}]
  (ok (db/update-todo-sort {:db db/conn
                            :origins origins
                            :response (Integer/parseInt response)
                            :target (Integer/parseInt response)})))

(defroutes api-routes
  (POST "/login" [] login)
  (GET "/test-api" [] (check-api-token test-api))
  (GET "/blogs" [] (check-api-token get-blogs))
  (PUT "/update-blog/:id" [] (check-api-token update-blog))
  (POST "/create-blog" [] (check-api-token create-blog))
  (GET "/todos" [] (check-api-token get-todos))
  (PUT "/update-todo/:id" [] (check-api-token update-todo))
  (POST "/create-todo" [] (check-api-token create-todo))
  (DELETE "/delete-todo" [] (check-api-token delete-todo))
  (POST "/tree-todo-generate" [] (check-api-token tree-todo-generate))
  (POST "/record-event" [] (check-api-token record-event))
  (POST "/update-todo-sort" [] (check-api-token update-todo-sort)))
