(ns jimw-clj.db.core
  (:require
    [cheshire.core :refer [generate-string parse-string]]
    [clojure.java.jdbc :as jdbc]
    [conman.core :as conman]
    [jimw-clj.config :refer [env]]
    [mount.core :refer [defstate]]
    [honeysql.core :as sql]
    [honeysql.helpers :as h]
    [taoensso.timbre :refer [error debug info]])
  (:import org.postgresql.util.PGobject
           java.sql.Array
           clojure.lang.IPersistentMap
           clojure.lang.IPersistentVector
           [java.sql
            BatchUpdateException
            Date
            Timestamp
            PreparedStatement]))

(defstate ^:dynamic *db*
  :start (conman/connect! {:jdbc-url #_(env :database-url)
                           "postgresql://jim:123456@127.0.0.1:5432/blackberry"})
  :stop (conman/disconnect! *db*))

(conman/bind-connection *db* "sql/queries.sql")

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

;; .e.g : (jconn *db* (-> (h/select :*) (h/from :navs)))
(defn jconn [conn sqlmap]
  (let [sql-str (sql/format sqlmap)]
    (info (str "SQL: " sql-str))
    (jdbc/query conn sql-str)))

(defn jconn1 [conn sqlmap]
  (first (jdbc/query conn (sql/format sqlmap))))

;; (first-nav {:db conn :past-id 13})
(defn first-nav [{:keys [db past-id]}]
  (jconn1 db
          (-> (h/select :*)
              (h/from :navs)
              (h/where [:= :past_id past-id])
              (h/order-by [:updated_at :desc])
              (h/limit 1))))

;; (get-nav-by-past-id {:db conn :past-id 13 :parid 279})
(defn get-nav-by-past-id [{:keys [db parid past-id]}]
  (jconn db
         (-> (h/select :*)
             (h/from :navs)
             (h/where
              [:and
               [:= :past_id past-id]
               [:= :parid parid]]))))

#_((tree (:id (first-nav {:db conn :past-id 13})))
   (fn [id]
     (get-nav-by-past-id {:db conn :past-id 13 :parid id})))
(def tree
  (fn [id]
    (fn [par]
      (map
       (fn [idd]
         ((tree (idd :id)) par))
       (par id)))))

;; (search-blogs {:db *db* :q "s"})
(defn search-blogs [{:keys [db limit offset q]}]
  (jconn db
         (-> (h/select :*)
             (h/from :blogs)
             (h/limit limit)
             (h/offset offset)
             (h/order-by [:id :desc])
             (h/where (when (seq q)
                        [:or [:like :name (str "%" q "%")]
                         [:like :content (str "%" q "%")]])))))
