(ns jimw-clj.db.core
  (:require
    [cheshire.core :refer [generate-string parse-string]]
    [clojure.java.jdbc :as jdbc]
    [conman.core :as conman]
    [jimw-clj.config :refer [env]]
    [mount.core :refer [defstate]]
    [honeysql.core :as sql]
    [honeysql.helpers :as h]
    [taoensso.timbre :refer [error debug info]]
    [buddy.hashers :as hashers]
    [jimw-clj.config :as config]
    [hikari-cp.core :as pool]
    [clojure.java.shell :as shell])
  (:import org.postgresql.util.PGobject
           java.sql.Array
           clojure.lang.IPersistentMap
           clojure.lang.IPersistentVector
           [java.sql
            BatchUpdateException
            Date
            Timestamp
            PreparedStatement]))

(defstate conn
  :start (try
           {:datasource
            (pool/make-datasource
             (:datasource-options
              @config/jimw-conf))}
           (catch Throwable e
             (info (str e ", 连接池连接失败!"))
             (System/exit 1)))
  :stop (pool/close-datasource conn))

(defn to-date [^java.sql.Date sql-date]
  (-> sql-date (.getTime) (java.util.Date.)))

(extend-protocol jdbc/IResultSetReadColumn
  Date
  (result-set-read-column [v _ _] (to-date v))

  Timestamp
  (result-set-read-column [v _ _] (to-date v))

  Array
  (result-set-read-column [v _ _] (vec (.getArray v)))

  PGobject
  (result-set-read-column [pgobj _metadata _index]
    (let [type  (.getType pgobj)
          value (.getValue pgobj)]
      (case type
        "json" (parse-string value true)
        "jsonb" (parse-string value true)
        "citext" (str value)
        value))))

(extend-type java.util.Date
  jdbc/ISQLParameter
  (set-parameter [v ^PreparedStatement stmt ^long idx]
    (.setTimestamp stmt idx (Timestamp. (.getTime v)))))

(defn to-pg-json [value]
      (doto (PGobject.)
            (.setType "jsonb")
            (.setValue (generate-string value))))

(extend-type clojure.lang.IPersistentVector
  jdbc/ISQLParameter
  (set-parameter [v ^java.sql.PreparedStatement stmt ^long idx]
    (let [conn      (.getConnection stmt)
          meta      (.getParameterMetaData stmt)
          type-name (.getParameterTypeName meta idx)]
      (if-let [elem-type (when (= (first type-name) \_) (apply str (rest type-name)))]
        (.setObject stmt idx (.createArrayOf conn elem-type (to-array v)))
        (.setObject stmt idx (to-pg-json v))))))

(extend-protocol jdbc/ISQLValue
  IPersistentMap
  (sql-value [value] (to-pg-json value))
  IPersistentVector
  (sql-value [value] (to-pg-json value)))

#_(def conn {:dbtype "postgresql"
           :dbname "blackberry"
           :host "127.0.0.1"
           :user "jim"
           :password "123456"})

;; .e.g : (jconn conn (-> (h/select :*) (h/from :navs)))
(defn jconn [conn sqlmap]
  (let [sql-vec (sql/format sqlmap)]
    (debug (str "SQL: " sql-vec))
    (jdbc/query conn sql-vec)))

(defn jconn1 [conn sqlmap]
  (let [sql-vec (sql/format sqlmap)]
    (debug (str "SQL: " sql-vec))
    (first (jdbc/query conn sql-vec))))

(defn jc1 [conn sqlmap]
  (let [sql-vec (sql/format sqlmap)
        sql-returning (apply vector (str (first sql-vec) " RETURNING *") (rest sql-vec))]
    (debug (str "SQL: " sql-returning))
    (first (jdbc/query conn sql-returning))))

;; (first-nav {:db conn :blog 4857})
(defn first-nav [{:keys [db blog]}]
  (jconn1 db
          (-> (h/select :*)
              (h/from :todos)
              (h/where [:= :blog blog])
              (h/order-by [:updated_at :desc])
              (h/limit 1))))

;; (get-nav-by-past-id {:db conn :blog 4857 :parid 50})
(defn get-nav-by-past-id [{:keys [db parid blog]}]
  (jconn db
         (-> (h/select :*)
             (h/from :todos)
             (h/where
              [:and
               [:= :blog blog]
               [:= :parid parid]]))))

;; ((get-nav-by-id {:db conn :id 50}) :content)
(defn get-nav-by-id [{:keys [db id]}]
  (jconn1 db
          (-> (h/select :*)
              (h/from :todos)
              (h/where [:= :id id]))))

(def tree-fn
  (fn [id output-fn]
    (fn [par]
      (map
       (fn [idd]
         (output-fn idd)
         ((tree-fn (idd :id) output-fn) par))
       (par id)))))

(def tree-out-puts (atom ()))

;; TODO: 机器学习,自动分行,断句,连接词和介词换行
(defn replace-tree-enter
  [st]
  (-> st
      (clojure.string/replace #",|\.|，|。| |:|=>" "\n")
      (clojure.string/replace "的" "的\n")
      (clojure.string/replace "和" "和\n")
      (clojure.string/replace "以" "以\n")))
  
;; (tree-todo-generate {:db conn :blog 4859})
(defn tree-todo-generate
  [{:keys [db blog]}]
  (let [_ (reset! tree-out-puts (list))
        output-fn
        (fn [idd]
          (swap!
           tree-out-puts conj
           (str "\"" (replace-tree-enter (:content
                                          ;;
                                          (let [res-1 (get-nav-by-id {:db db :id (:parid idd)})]
                                            (if (= (:done res-1) true) {:content "done"} res-1))
                                          ;;
                                          ))
                "\"" " -> "
                "\"" (replace-tree-enter (:content
                                          ;;
                                          (if (= (:done idd) true) {:content "done"} idd)
                                          ;;
                                          )) "\"\n")))]
    ((tree-fn
      (:id (first-nav {:db db :blog blog}))
      output-fn)
     (fn [id]
       ;;
       (get-nav-by-past-id {:db db :blog blog :parid id})
       ;;
       ))))

;; (writer-tree-file 4857)
(defn writer-tree-file
  [blog]
  (-> (Thread.
       (fn []
         (with-open [wtr (clojure.java.io/writer
                          (str "resources/public/todos-" blog ".gv"))]
           (.write wtr "digraph G {\n")
           (doseq [line @tree-out-puts]
             (.write wtr line))
           (.write wtr "\n}"))))
      .start))

(defn agg-json-object
  [kvs]
  (honeysql.core/call
   :coalesce (->> (for [[k v] kvs]
                    [(if (keyword? k) (name k) k) v])
                  (apply concat)
                  (apply honeysql.core/call :jsonb_build_object)
                  (honeysql.core/call :jsonb_agg))
   (honeysql.core/call :cast "[]" :jsonb)))

(def todos-subquery
  (-> (h/select (agg-json-object
                 {:id :id
                  :parid :parid
                  :content :content
                  :done :done
                  :created_at :created_at
                  :updated_at :updated_at}))
      (h/from :todos)))

;; (search-blogs {:db conn :q "肌肉记忆"})
(defn search-blogs [{:keys [db limit offset q]}]
  (jconn db
         (-> (h/select :id :name :content :created_at :updated_at
                       [(-> todos-subquery
                            (h/where [:= :blogs.id :todos.blog]))
                        :todos])
             (h/from :blogs)
             (h/limit limit)
             (h/offset offset)
             (h/order-by [:id :desc])
             (h/where (when (seq q)
                        (let [q-list (clojure.string/split q #" ")]
                          (apply conj [:and]
                                 (map #(vector
                                        :or
                                        [:like :name (str "%" % "%")]
                                        [:like :content (str "%" % "%")])
                                      q-list))))))))

;; (update-blog {:db conn :id 5000 :name nil :content "dasdsdas"})
(defn update-blog [{:keys [db id name content]}]
  (jc1 db
       (->  (h/update :blogs)
            (h/sset (->> {:name    (when (seq name) name)
                          :content (when (seq content) content)
                          :updated_at (honeysql.core/call :now)}
                         (remove (fn [x]  (nil? (last x))))
                         (into {})))
            (h/where [:= :id id]))))

;; (create-blog {:db conn :name "测试" :content "aaaaabbbccc"})
(defn create-blog [{:keys [db name content]}]
  (jc1 db
       (->  (h/insert-into :blogs)
            (h/values [{:name name
                        :content content}]))))

;; (search-todos {:db conn :q "a" :blog 4857})
(defn search-todos [{:keys [db blog q]}]
  (jconn db
         (-> (h/select :*)
             (h/from :todos)
             (h/order-by [:id :desc])
             (h/where (when (seq q)
                        [:like :content (str "%" q "%")]))
             (h/merge-where (when (pos? blog) [:= :blog blog])))))

;; (create-todo {:db conn :content "aaaaabbbccc" :parid 3 :blog 2222})
(defn create-todo [{:keys [db parid blog content]}]
  (jc1 db
       (->  (h/insert-into :todos)
            (h/values [{:content content
                        :parid   parid
                        :blog    blog}]))))

;; (update-todo {:db conn :id 58 :content "aaaaabbbccctt" :blog 4857 :done nil})
;; (update-todo {:db conn :id 58 :content "aaaaabbbccctt" :blog 4857 :done false})
;; (update-todo {:db conn :id 58 :content "aaaaabbbccctt" :blog 4857 :done true})
;; (update-todo {:db conn :id 58 :content "aaaaabbbccctt" :blog 4857 :done "false"})
;; (update-todo {:db conn :id 58 :content "aaaaabbbccctt" :blog 4857 :done "true"})
(defn update-todo [{:keys [db id parid blog content done]}]
  (jc1 db
       (->  (h/update :todos)
            (h/sset (->> {;;:parid    (when (pos? parid) parid)
                          :content (when (seq content) content)
                          :blog (when (pos? blog) blog)
                          :done (let [ndone (if (string? done) (Boolean/valueOf done) done)]
                                  (when
                                      (not (nil? ndone))
                                    ndone))
                          :updated_at (honeysql.core/call :now)}
                         (remove (fn [x]  (nil? (last x))))
                         (into {})))
            (h/where [:= :id id]))))

;; (delete-todo {:db conn :id 3})
(defn delete-todo [{:keys [db id]}]
  (jc1 db
       (-> (h/delete-from :todos)
           (h/where [:= :id id]))))

(defn insert-user [{:keys [db username password]}]
  (jc1 db
       (-> (h/insert-into :users)
           (h/values [{:username username
                       :password (hashers/derive password)}]))))

(defn get-user-by-username [{:keys [db username]}]
  (jconn1 db
          (-> (h/select :*)
              (h/from :users)
              (h/where [:= :username username]))))

(defn import-project-file-to-blog
  []
  (let [file-names
        (->
         (shell/sh "find" "lib" "-name" "*.clj*")
         :out
         (clojure.string/split #"\n"))
        content-fn (fn [file-name] (str "```clojure\n" (slurp file-name) "\n```"))]
    (for [file-name file-names]
      (create-blog {:db conn :name file-name :content (content-fn file-name)}))))
