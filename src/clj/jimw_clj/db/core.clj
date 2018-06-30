(ns jimw-clj.db.core
  (:require
    [cheshire.core :refer [generate-string parse-string]]
    [clojure.java.jdbc :as jdbc]
    ;;[conman.core :as conman]
    [jimw-clj.config :refer [env]]
    [mount.core :refer [defstate]]
    [mount.lite :as lite]
    [honeysql.core :as sql]
    [honeysql.helpers :as h]
    [honeysql-postgres.helpers :as hp]
    [taoensso.timbre :refer [error debug info]]
    [buddy.hashers :as hashers]
    [jimw-clj.config :as config]
    [hikari-cp.core :as pool]
    [clojure.java.shell :as shell]
    [clojure.string :as str]
    [clojure.pprint :as pp]
    ;;[clj-jri.R :as R]
    [cheshire.core :as cjson]
    [clojure.core.async :as async]
    [jimw-clj.db.racket :as racket]
    [jimw-clj.db.scheme :as scheme]
    [jimw-clj.db.matlab :as matlab]
    [jimw-clj.db.emacs :as emacs]
    [jimw-clj.db.java :as java]
    ;;[hanlping.core :as han]
    [pdfboxing.text :as text]
    [pdfboxing.split :as pdf]
    [pdfboxing.form :as form]
    [pdfboxing.split :as split]
    [pdfboxing.info :as info]
    ;;[incanter.charts :as c]
    ;;[incanter.core :as i]
    ;; 代码语义搜索
    [jimw-clj.db.crf :as crf]
    [clojurewerkz.neocons.rest :as nr]
    [clojurewerkz.neocons.rest.nodes :as nn]
    [clojurewerkz.neocons.rest.relationships :as nrl]
    [hickory.core :as hickory]
    [hickory.select :as hs]
    [markdown.core :refer [md-to-html-string]])
  (:import org.postgresql.util.PGobject
           java.sql.Array
           clojure.lang.IPersistentMap
           clojure.lang.IPersistentVector
           [java.sql
            BatchUpdateException
            Date
            Timestamp
            PreparedStatement]
           [java.sql SQLException]
           [java.lang IndexOutOfBoundsException]
           [com.huaban.analysis.jieba JiebaSegmenter]
           ;; 对于JiebaSegmenter类里面的`static enum SegMode`的引用
           [com.huaban.analysis.jieba JiebaSegmenter$SegMode]
           [net.glxn.qrgen.javase QRCode]
           [com.lsj.trans LANG]
           [com.lsj.trans.factory TFactory TranslatorFactory]
           [com.github.javaparser JavaParser ParseResult ParserConfiguration]
           [com.github.javaparser.ast.stmt Statement]))

;; (zh->en "高斯分布")  => "Gaussian distribution"
(defn zh->en
  [stri]
  (let [factory (TranslatorFactory.)]
    (let [res (.trans (.get factory "google") LANG/ZH LANG/EN stri)]
      (if (re-matcher #"<HTML><HEAD>(.*)" res)
        (.trans (.get factory "jinshan") LANG/ZH LANG/EN stri) ;;OK
        res))))

(defn en->zh
  [stri]
  (let [factory (TranslatorFactory.)]
    (.trans (.get factory "google") LANG/EN LANG/ZH stri)))

(def segmenter (JiebaSegmenter.))

;;=> (["这是" 0 2] ["一个" 2 4] ["伸手不见五指" 4 10] ["的" 10 11] ["黑夜" 11 13] ["。" 13 14] ["我" 14 15] ["叫" 15 16] ["steve" 16 21] ["，" 21 22] ["我" 22 23] ["爱" 23 24] ["北京" 24 26])
(defn jieba-seg [st]
  (map
   (fn [seg] (vector (.word seg) (.startOffset seg) (.endOffset seg)))
   (->
    segmenter
    (.process st JiebaSegmenter$SegMode/SEARCH))))

;; (jieba-wordcloud "这是一个伸手不见五指的黑夜。我叫Steve，我爱北京, 我爱Clojure")
;; => (["我" 3] ["爱" 2] ["clojure" 1] ["黑夜" 1] ["这是" 1] ["一个" 1] ["不见" 1] ["伸手不见五指" 1] ["的" 1] ["北京" 1] ["伸手" 1] ["," 1] ["steve" 1] ["。" 1] ["，" 1] ["叫" 1] [" " 1] ["五指" 1])
(defn jieba-wordcloud [st]
  (->>
   (->
    segmenter
    (.process st JiebaSegmenter$SegMode/INDEX))
   (map (fn [seg] (vector (.word seg) (.startOffset seg) (.endOffset seg))))
   (map first)
   (group-by identity)
   (map (fn [x] (vector (first x) (count (last x)))))
   (sort-by #(* -1 (last %)))))

;; Load R lib & function
(def r-lib
  ["library(tm)"
   "library(wordcloud)"
   "library(memoise)"
   "library(RJSONIO)"])

#_(if (get (System/getenv) "RUN_REVAL") nil
    (do
      ;; load r lib
      (if (R/eval r-lib) (info "load R lib ok...") (throw (Exception. "load R lib failure !")))      
      (def get-term-matrix-path (R/eval "paste(getwd(),'/src/R/getTermMatrix.R', sep='')"))
      ;; load getTermMatrix function
      (R/eval (str "source('" get-term-matrix-path "')"))))

(if (and (= (get (System/getenv) (name :OS_TYPE)) (name :MACOX)) (not= (get (System/getenv) (name :neo4j_stat)) "false"))
  (lite/defstate neo4j-conn
    :start
    (nr/connect "http://neo4j:123456@localhost:7474/db/data/"))
  (lite/defstate neo4j-conn
    :start "todo: install neo4j in production"))

(lite/defstate conn
  :start
  (let [{:keys [database-name adapter auto-commit register-mbeans password
                port-number username max-lifetime minimum-idle connection-timeout
                server-name read-only maximum-pool-size idle-timeout
                validation-timeout pool-name]}
        (:datasource-options @config/jimw-conf)]
    (try
      {:datasource
       (pool/make-datasource
        {:database-name      database-name     
         :adapter            adapter           
         :auto-commit        auto-commit       
         :register-mbeans    register-mbeans   
         :password           password          
         :port-number        port-number       
         :username           username          
         :max-lifetime       max-lifetime      
         :minimum-idle       minimum-idle      
         :connection-timeout connection-timeout
         :server-name        server-name       
         :read-only          read-only         
         :maximum-pool-size  maximum-pool-size 
         :idle-timeout       idle-timeout      
         :validation-timeout validation-timeout
         :pool-name          pool-name})}
      (catch Throwable e
        (info (str e ", jimw-clj 连接池连接失败!"))
        (System/exit 1))))
  :stop (pool/close-datasource @conn))

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
    ;;(debug (str "SQL: " sql-returning))
    ;;为什么调试信息可以打印出来？in cider.如何运用代码语音搜索？
    ;; 充分利用截图来分析
    (if (re-matcher #"(.*)贾维斯(.*)" (str sql-returning))
      (debug (str "执行命令: " sql-returning)))
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

;; (get-nav-max-id {:db conn :blog 22791})
(defn get-nav-max-id [{:keys [db blog]}]
  (jconn db
         (-> (h/select :id)
             (h/from :todos)
             (h/where [:= :blog blog])
             (h/order-by [:id :desc])
             (h/limit 1))))

;; (get-nav-count {:db conn :blog 4933}) => 16
;; (count (list 895 892 890 888 894 893 891 889 887 886 885 903 901 902 900)) ;; => 15
(defn get-nav-count [{:keys [db blog]}]
  (-> (jconn db
             (-> (h/select (sql/call :count :*))
                 (h/from :todos)
                 (h/where [:= :blog blog])))
      first :count))

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

;; (replace-tree-enter "这是一个伸手不见五指的黑夜。我叫Steve，我爱北京, 我爱Clojure")
;; => "这是一个伸手不见五指\n的黑夜。\n我叫steve\n，我爱\n北京, \n我爱clojure"
(defn replace-tree-enter
  [st]
  (->> st
       jieba-seg
       (map first)
       (partition 3 3 [""])
       (map (fn [x] (clojure.string/join "" x)))
       (clojure.string/join "\n")))
  
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
         (do
           (with-open [wtr (clojure.java.io/writer
                            (str "resources/public/todos-" blog ".gv"))]
             (.write wtr "digraph G {\n")
             (doseq [line @tree-out-puts]
               (.write wtr line))
             (.write wtr "\n}"))
           (shell/sh
            "dot" "-Tsvg" (str "resources/public/todos-" blog ".gv") "-o"
            (str "resources/public/todos-" blog ".svg")))))
      .start))

(defn tree-fn-new [id par res output-fn sum ids]
  (map
   (fn [idd]
     (output-fn res idd)
     (swap! ids conj (idd :id))
     (if (>= (inc (count @ids)) sum)
       @res
       (tree-fn-new (idd :id) par res output-fn sum ids)))
   (par id)))

;; (println (tree-todo-generate-new {:db conn :blog 4941}))
(defn tree-todo-generate-new
  [{:keys [db blog]}]
  (let [_ (jc1 db
               (->  (h/update :todos)
                    (h/sset {:updated_at (honeysql.core/call :now)})
                    (h/where [:and
                              [:= :blog blog]
                              [:= :parid 1]])))
        output-fn
        (fn [res idd]
          (swap!
           res conj
           (str "\"" (replace-tree-enter (:content
                                          (let [res-1 (get-nav-by-id {:db db :id (:parid idd)})]
                                            (if (= (:done res-1) true) {:content "done"} res-1))))
                "\"" " -> "
                "\"" (replace-tree-enter (:content
                                          (if (= (:done idd) true) {:content "done"} idd))) "\"\n")))
        blog-nav-sum (get-nav-count {:db db :blog blog})]
    (str "digraph G {\n"
         (->> (tree-fn-new
               (:id (first-nav {:db db :blog blog}))
               (fn [id]
                 (get-nav-by-past-id {:db db :blog blog :parid id}))
               (atom (list)) output-fn blog-nav-sum (atom (list)))
              flatten (clojure.string/join "")) "\n}")))

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
                 {:sort_id :id
                  :id :sort_id ;; TODOS: 为了减少前端大量修改,而修改后端
                  :parid :parid
                  :content :content
                  :done :done
                  :created_at :created_at
                  :updated_at :updated_at
                  :wctags :wctags
                  :app_id         :app_id        
                  :file           :file          
                  :islast         :islast        
                  :percent        :percent       
                  :begin          :begin         
                  :mend           :mend          
                  :origin_content :origin_content}))
      (h/from :todos)))

(def events-subquery
  (-> (h/select
       [(honeysql.core/raw "array_agg(\"event_data\" ORDER BY id ASC)") :event_data])
      (h/from :events)))

;; 训练的三元组数据: (jconn @conn (h/select (sql/call :now))) ;; => ({:now #inst "2018-06-10T08:32:38.901-00:00"})
;; SELECT extract(epoch from updated_at)  FROM blogs; =>
;; (jconn @conn (-> (h/select [(honeysql.core/raw "extract(epoch from updated_at)") :unix_time]) (h/from :blogs) (h/limit 1)))
;; (search-blogs {:db conn :q "肌肉记忆"})
(defn search-blogs [{:keys [db limit offset q source project orderby]}]
  (let [res (jconn db
                   (-> (h/select :id :name :content :created_at :updated_at
                                 [(honeysql.core/raw "extract(epoch from updated_at)") :unix_time]
                                 [(-> todos-subquery
                                      (h/where [:= :blogs.id :todos.blog]))
                                  :todos]
                                 [(-> events-subquery
                                      (h/where [:= :blogs.id :events.blog]))
                                  :stags])
                       (h/from :blogs)
                       (h/limit limit)
                       (h/offset offset)
                       (h/order-by (if orderby
                                     [:id :desc]
                                     [:updated_at :desc]))
                       (h/merge-where (when (seq q)
                                        (let [q-list (clojure.string/split q #" ")]
                                          (apply conj [:and]
                                                 (map #(vector
                                                        :or
                                                        [:like :name (str "%" % "%")]
                                                        [:like :content (str "%" % "%")])
                                                      q-list)))))
                       (h/merge-where (when (seq project)
                                        [:= :project project]))
                       (h/merge-where [:= :source_type (honeysql.core/call :cast source :SOURCE_TYPE)])))]
    (if (= source "WEB_ARTICLE")
      res
      res)
    )
  )

(defn get-blog-wctags [{:keys [db id]}]
  (jconn1 db
          (-> (h/select :id :wctags)
              (h/from :blogs)
              (h/where [:= :id id]))))

;; (update-blog {:db conn :id 5000 :name nil :content "dasdsdas"})
(defn update-blog [{:keys [db id name content source_type project]}]
  (let [res (jc1 db (->  (h/update :blogs)
                         (h/sset (->> {:name    (when (seq name) name)
                                       :content (when (seq content) content)
                                       :source_type (when (seq source_type)
                                                      (honeysql.core/call :cast source_type :SOURCE_TYPE))
                                       :project (when (seq project)
                                                  project)
                                       :updated_at (honeysql.core/call :now)}
                                      (remove (fn [x]  (nil? (last x))))
                                      (into {})))
                         (h/where [:= :id id])))
        update-wctags (fn []
                        (let [res-json 111 #_(R/eval
                                        (str "toJSON(getTermMatrix(\""
                                             (->>
                                              (clojure.string/split (:content res) #"\W")
                                              (remove #(= % ""))
                                              (clojure.string/join " ")) "\"))"))]
                          ;;(info (str "JSON:" res-json "========"))
                          (jc1 conn
                               (->  (h/update :blogs)
                                    (h/sset {:wctags (sql/call
                                                      :cast
                                                      (cjson/generate-string
                                                       (cjson/parse-string
                                                        res-json)) :jsonb)})
                                    (h/where [:= :id (:id res)])))))]
    #_(async/go
        (async/<! (async/timeout (* 2 1000)))
        (update-wctags))
    res))

;; (create-blog {:db @conn :name "测试" :content "aaaaabbbccc" :source_type "REVERSE_ENGINEERING"})
(defn create-blog [{:keys [db name content source_type project]}]
  (let [res (jc1 db (->  (h/insert-into :blogs)
                         (h/values [{:name name
                                     :content content
                                     :project (if project project "BLOG")
                                     :created_at (sql/call :now)
                                     :updated_at (sql/call :now)
                                     :source_type
                                     (if (nil? source_type)
                                       (honeysql.core/call :cast "BLOG" :SOURCE_TYPE)
                                       (honeysql.core/call :cast (clojure.core/name source_type) :SOURCE_TYPE))}])))]
    res))

#_(jc1 @conn (->  (h/insert-into :blogs)
                  (h/values [{:name "aaaaa"
                              :content "bbbbbb"
                              :source_type (honeysql.core/call :cast "BLOG" :SOURCE_TYPE) }])))
;;=> OK => {:page_id nil, :yardoc false, :content "bbbbbb", :name "aaaaa", :source_type "BLOG", :content_type nil, :updated_at nil, :project nil, :id 5859, :position nil, :wctags {}, :updatecount nil, :visible false, :created_at nil}

#_(jc1 @conn (->  (h/insert-into :blogs)
                  (h/values [{:name "aaaaa"
                              :content "bbbbbb"
                              :source_type (honeysql.core/call :cast "REVERSE_ENGINEERING" :SOURCE_TYPE) }])))
;; => {:page_id nil, :yardoc false, :content "bbbbbb", :name "aaaaa", :source_type "REVERSE_ENGINEERING", :content_type nil, :updated_at nil, :project nil, :id 5860, :position nil, :wctags {}, :updatecount nil, :visible false, :created_at nil}


;; (create-blog-and-root {:db conn :name "测试" :content "aaaaabbbccc"})
;;  => {:blog 37576, :todo 120}
(defn create-blog-and-root [{:keys [db name content]}]
  (let [blog (jc1 db (->  (h/insert-into :blogs)
                          (h/values [{:name name
                                      :content content
                                      :updated_at (sql/call :now)
                                      :source_type (honeysql.core/call :cast "WEB_ARTICLE" :SOURCE_TYPE)}])))
        todo (jc1 db
                  (->  (h/insert-into :todos)
                       (h/values [{:content "root"
                                   :parid   1
                                   :blog    (:id blog)}])))]
    {:blog (:id blog) :todo (:id todo)}))

;; (search-todos {:db conn :q "a" :blog 4857})
(defn search-todos [{:keys [db blog q]}]
  (jconn db
         (-> (h/select :*)
             (h/from :todos)
             (h/order-by [:id :desc])
             (h/where (when (seq q)
                        [:like :content (str "%" q "%")]))
             
             (h/merge-where (when (pos? blog) [:= :blog blog])))))

;; 有todos的更新或者event的更新: 这个博客都会被排序置顶
(defn update-blog-updated-time [{:keys [db blog]}]
  (jc1 db (->  (h/update :blogs)
               (h/sset {:updated_at (honeysql.core/call :now)})
               (h/where [:= :id blog]))))

;; (create-todo {:db conn :content "aaaaabbbccc" :parid 3 :blog 2222})
(defn create-todo [{:keys [db parid blog content]}]
  (let [res (jc1 db
                 (->  (h/insert-into :todos)
                      (h/values [{:content content
                                  :parid   parid
                                  :blog    blog}])))
        _ (update-blog-updated-time {:db db :blog blog})]
    res)
  )

(defn create-todo-app [{:keys [db parid blog content
                               app_id file islast percent begin mend]}]
  (let [res (jc1 db
                 (->  (h/insert-into :todos)
                      (h/values [{:content content
                                  :parid   parid
                                  :blog    blog
                                  :app_id  app_id  
                                  :file    file    
                                  :islast  islast  
                                  :percent percent 
                                  :begin   begin   
                                  :mend     mend
                                  :origin_content content}])))
        _ (update-blog-updated-time {:db db :blog blog})]
    res)
  )

;; (update-todo {:db conn :id 58 :content "aaaaabbbccctt" :blog 4857 :done nil})
;; (update-todo {:db conn :id 58 :content "aaaaabbbccctt" :blog 4857 :done false})
;; (update-todo {:db conn :id 58 :content "aaaaabbbccctt" :blog 4857 :done true})
;; (update-todo {:db conn :id 58 :content "aaaaabbbccctt" :blog 4857 :done "false"})
;; (update-todo {:db conn :id 58 :content "aaaaabbbccctt" :blog 4857 :done "true"})
(defn update-todo [{:keys [db id parid blog content done]}]
  (let [res (jc1 db
                 (->  (h/update :todos)
                      (h/sset (->> {:parid  parid
                                    :content (when (seq content) content)
                                    :blog (when (pos? blog) blog)
                                    :done (let [ndone (if (string? done) (Boolean/valueOf done) done)]
                                            (when
                                                (not (nil? ndone))
                                              ndone))
                                    :updated_at (honeysql.core/call :now)}
                                   (remove (fn [x]  (nil? (last x))))
                                   (into {})))
                      (h/where [:= :id id])))
        _ (update-blog-updated-time {:db db :blog blog})]
    res)
  )

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
  [db]
  (let [file-names
        (->
         (shell/sh "find" "lib" "-name" "*.clj*")
         :out
         (clojure.string/split #"\n"))
        content-fn (fn [file-name] (str "```clojure\n" (slurp file-name) "\n```"))]
    (for [file-name file-names]
      (create-blog {:db db :name file-name :content (content-fn file-name)}))))

;; 测试新的项目导入是否解析报错: => TODOS: 添加一个填入Github地址,然后直接导入project的s表达式到db里面
;; (read-string-for-pro (fn [code-list file-name] (map first code-list)) "incanter") ;; 导入incanter的develop分支(最新)
;; (import-project-s-exp-to-blog @conn "incanter")
;; (count (jconn @conn (-> (h/select :id) (h/from :blogs) (h/where [:like :name "%jimw-code/incanter%"])))) ;;=> 1690
;; (jconn @conn (-> (h/delete-from :blogs) (h/where [:like :name "%jimw-code/foreclojure-android%"])))
(defn read-string-for-pro
  [op-fn & project]
  (let [file-names
        (->>
         (->
          (shell/sh "find"
                    (if project
                      (str "lib/jimw-code/" (first project))
                      "lib") "-name" "*.clj*") :out
          (clojure.string/split #"\n"))
         (remove #(or
                   (= % "lib/jimw-code/clojure/test/clojure/test_clojure/reader.cljc")
                   (= % "lib/jimw-code/clojure/test/clojure/test_clojure/java_interop.clj"))))
        split-code
        (fn [file-name]
          (let [_ (prn (str file-name " >>>>>>"))
                remove-invalid-token
                (fn [st]
                  (-> st
                      (str/replace #"::" "double-colon-")
                      (str/replace #"\"\"" "\"jimw_empty_string\"")
                      (str/replace #"#js" "" #_"the-tag-js")
                      (str/replace #"#\?" "")
                      (str/replace #"#\"" "\"")
                      (str/replace #"\\\." "-back-slant1-dot-")
                      (str/replace #"\\\\" "-back-slant2-")
                      (str/replace #"\\d" "-back-slant3-num-")
                      (str/replace #"\\-" "-back-slant4--")
                      (str/replace #"\\\?" "-back-slant5-que-")
                      (str/replace #"\\\*" "-back-slant6-que-")
                      (str/replace #"\\\/" "-back-slant7-que-")
                      (str/replace #"\\\)" "-back-slant8-que-")
                      (str/replace #"\\\#" "-back-slant9-que-")
                      (str/replace #"\\s" "-back-slant10-que-")
                      (str/replace "\\{" "-back-slant11-que-")
                      (str/replace "\\}" "-back-slant12-que-")
                      (str/replace "\\W" "-back-slant13-que-")
                      (str/replace "\\w" "-back-slant14-que-")
                      (str/replace "\\S" "-back-slant15-que-")
                      (str/replace "\\$" "-back-slant16-que-")
                      (str/replace "\\(" "-back-slant17-que-")
                      (str/replace "\\Q" "-back-slant18-que-")
                      (str/replace "\\E" "-back-slant19-que-")
                      (str/replace "\\[" "-back-slant20-que-")
                      (str/replace "\\]" "-back-slant21-que-")
                      (str/replace "\\>" "-back-slant22-que-")
                      (str/replace "\\:" "-back-slant23-que-")
                      (str/replace "\\<" "-back-slant24-que-")
                      (str/replace "\\!" "-back-slant25-que-")
                      (str/replace "\\+" "-back-slant26-que-")
                      (str/replace "#="  "")
                      (str/replace "#clojure.test_clojure.protocols.RecordToTestBoolHint" "")
                      (str/replace "#clojure.test_clojure.protocols.RecordToTestLongHint" "")
                      (str/replace "#clojure.test_clojure.protocols.TestNode" "")
                      (str/replace "#clojure.test_clojure.protocols.TypeToTestLiterals" "")
                      (str/replace "#clojure.test_clojure.protocols.RecordToTestLiterals" "")
                      (str/replace "#clojure.test_clojure.protocols.TypeToTestHugeFactories" "")
                      (str/replace "#clojure.test_clojure.protocols.TypeToTestFactory" "")
                      (str/replace "#clojure.test_clojure.protocols.RecordToTestFactories" "")
                      (str/replace "#clojure.test_clojure.protocols.RecordToTestStatics3" "")
                      (str/replace "#clojure.test_clojure.protocols.RecordToTestStatics2" "")
                      (str/replace "#clojure.test_clojure.protocols.RecordToTestStatics1" "")
                      (str/replace "#clojure.test_clojure.compilation.Y[1]" "")))
                list-init (fn [st] (str "( " st " )"))
                code-list (->>
                           (slurp file-name)
                           remove-invalid-token
                           list-init
                           read-string)]
            (op-fn code-list file-name)))]
    (for [file-name file-names]
      (split-code file-name))))

(defn import-project-s-exp-to-blog
  [db & project]
  (let [content-fn
        (fn [content]
          (str "```clojure\n"
               (pp/write content :dispatch pp/code-dispatch :stream nil)
               "\n```"))]
    (read-string-for-pro
     (fn [code-list file-name]
       (do
         (prn (str file-name " >>>>>>"))
         (map
          (fn [content] (do (create-blog {:db db
                                          :name file-name
                                          :content (content-fn content)
                                          :source_type "SEMANTIC_SEARCH"
                                          :project (first project)}) (first content)))
          code-list)))
     (if project (first project) nil))))

;; (insert-event {:db @conn :event_name "test" :info "dasdas" :event_data "32132"})
;; (insert-event {:db @conn :event_name "test" :info "dasdas" :event_data "32132" :blog 60142})
(defn insert-event [{:keys [db event_name info event_data blog]}]
  (let [res (jc1 db
                 (-> (h/insert-into :events)
                     (h/values [{:event_name event_name
                                 :info       (when (seq info) info)
                                 :blog       (if blog blog nil)
                                 :event_data (when (seq event_data) event_data)}])))
        _ (if blog
            (update-blog-updated-time {:db db :blog blog})
            nil)]
    res)
  )

(defn update-todo-sort
  [{:keys [db origins response target]}]
  (let [get-key-by-val (fn [value] (first (filter (comp #{value} origins) (keys origins))))
        is-up (> (get origins response) target)
        beetweens (->>
                   origins
                   (filter
                    (if is-up
                      #(and (> (get origins response) (last %))
                            (< (dec target) (last %)))
                      #(and (> (inc target) (last %))
                            (< (get origins response) (last %)))))
                   (map #(vector
                          (first %)
                          ((if is-up inc dec)
                           (last %)))))
        alls (conj beetweens [response target])]
    (for [item alls]
      (jc1 db
           (->  (h/update :todos)
                (h/sset {:sort_id (last item)})
                (h/where [:= :id (first item)]))))))

;; (get-sql-dot-index-number "lib/his_graph.dot")
;; => (["accounts" "7" "10"] ["billing_modes" "12" "15"] ... ...)
(defn get-sql-dot-index-number
  [filename]
  (->>
   (map
    #(-> (re-find #":(\d+):(.*)" %) rest)
    (->
     (shell/sh "./bin/grepdot.sh" filename)
     :out
     (clojure.string/split #"\n")
     rest))
   flatten
   (partition-by #(= % "  ];"))
   (remove #(= % (list "  ];")))
   (map #(vector (last (re-find #"\"(.*)\"" (nth % 1))) (first %) (last %)))))

;; (get-table-content-index "lib/his_graph.dot" 7 10)
(defn get-table-content-index
  [filename start end]
  (:out
   (shell/sh "sed" "-n" (str start "," end "p") filename)))

(defn import-sql-dot-table
  [db filename]
  (let [dots (get-sql-dot-index-number filename)]
    (for [dot dots]
      (jc1 db
           (-> (h/insert-into :sqldots)
               (h/values [{:name (first dot)
                           :content  (get-table-content-index
                                      filename (nth dot 1) (last dot))
                           :dot_type "TABLE"}]))))))

(defn import-sql-dot-relation
  [db filename]
  (let [dots (filter
              #(re-matches #"(.*)->(.*)" %)
              (->
               (slurp filename)
               (clojure.string/split #"\n")))]
    (for [dot dots]
      (jc1 db
           (-> (h/insert-into :sqldots)
               (h/values [{:name filename
                           :content dot
                           :dot_type "RELATION"}]))))))


;; (map :en_name (search-enzh {:db conn :zh_name "操作"}))
;;=> ("operator" "operation")
(defn search-enzh [{:keys [db zh_name]}]
  (jconn db
         (-> (h/select :*)
             (h/from :enzhs)
             (h/where [:like :zh_name (str "%" zh_name "%")]))))

;; (map :event_data (search-events {:db @conn :q "f"}))
(defn search-events [{:keys [db q]}]
  (jconn db
         (-> (h/select :*)
             (h/from :events)
             (h/where [:and
                       [:not= :event_data nil]
                       [:like :event_data (str "%" q "%")]])
             (h/order-by [:created_at :desc])
             (h/limit 10))))

;; (map->en conn "操作") ;; => "operator operation"
;; (map->en conn "operator apple") ;;=> "operator apple"
(defn map->en
  [db q]
  (if (seq q)
    (let [q-list (clojure.string/split q #" ")]
      (->>
       (map
        (fn [q-item]
          (if (re-matches #"(.*)[\u4e00-\u9fa5](.*)" q-item)
            (let [res
                  (map :en_name (search-enzh {:db db :zh_name q-item}))]
              (if (empty? res)
                "" res))
            q-item))
        q-list)
       flatten (clojure.string/join " "))) ""))

;; (translator-map2en @conn "f")
;;  => ("Lecun98.pdf" "Lecun98.pdf" "f Gaussian distribution"
(defn translator-map2en
  [db q]
  (if (seq q)
    (let [q-list (clojure.string/split q #" ")]
      (into
       (sorted-map-by <)
       (map-indexed
        vector
        (->>
         (map
          (fn [q-item]
            (let [res
                  (map
                   (fn [{:keys [event_data]}]
                     (if (re-matches #"(.*)[\u4e00-\u9fa5](.*)" event_data)
                       (zh->en event_data)
                       event_data))
                   (search-events {:db db :q q-item}))]
              (if (empty? res)
                (zh->en q) res))
            )
          q-list)
         flatten)))) ""))

#_(distinct
   (map :name
        (search-sqldots {:db conn :q "aaa patients"})))
;;=> ("aaa" "lib/his_graph.dot" "ooopatients" "patients")
(defn search-sqldots [{:keys [db q]}]
  (jconn db
         (-> (h/select :id :name :content :created_at :updated_at)
             (h/from :sqldots)
             (h/where (when (seq q)
                        (let [q-list (clojure.string/split
                                      (str/replace
                                       (map->en db q) "-" "_")
                                      #" ")]
                          (apply conj [:or]
                                 (map #(vector
                                        :or
                                        [:like :name (str "%" % "%")]
                                        [:like :content (str "%" % "%")])
                                      q-list))))))))

(defn get-all-defmodel
  [db]
  (->>
   (jconn db
          (-> (h/select :id :name :content :created_at :updated_at)
              (h/from :blogs)
              (h/order-by [:id :desc])
              (h/where
               [:and
                [:not-like :name "%_test.clj%"]
                [:like :content "%(defmodel%"]])))
   (map #(let [lis (-> % :content
                       (str/replace "```clojure\n" "")
                       (str/replace "\n```" "")
                       read-string
                       rest)]
           {:defmodel (str (first lis)) :defmodel-desc (nth lis 1) :model-key-val (-> lis rest rest rest)}))))

(defn get-model-key-val
  [model]
  (->>
   model
   (keep-indexed #(if (string? %2) [(dec %1) %2]))
   (map
    #(vector (nth model (first %))  (last %)))))

(defn to-clj-style
  [st]
  (str/replace st "_" "-"))

(defn to-sql-style
  [st]
  (str/replace st "-" "_"))

(defn replace-jimw
  [a b st]
  (prn (str "***************" a "----" b  "+++++" st))
  (str/replace
   (if (nil? st) "" st)
   (if (nil? a) "" a)
   (if (nil? b) "" b))
  )

(defmacro list->>
  [x forms]
  (loop [x x, forms forms]
    (if forms
      (let [form (first forms)
            _ (prn form)
            threaded (if (seq? form)
                       (with-meta `(~@(conj form 'replace-jimw) ~x) (meta form))
                       (list form x))]
        (recur threaded (next forms)))
      x)))

#_(update-sqldots-zh
   conn
   "medicine-stocktakings"
   "表描述"
   (get-model-key-val
    (:model-key-val
     (first
      (filter #(= (:defmodel %) "medicine-stocktakings") (get-all-defmodel conn))))))
(defn update-sqldots-zh
  [db model-name defmodel-desc model-key-val]
  (let [{:keys [id content]}
        (jconn1 db
                (-> (h/select :*)
                    (h/from :sqldots)
                    (h/where [:and
                              [:= :name (to-sql-style model-name)]
                              [:= :dot_type (sql/call :cast "TABLE" :dot_type)]])))
        replace-key-pair (map
                          (fn [item]
                            (list
                             (str " <" (to-sql-style (name (first item))) "> ")
                             (str " <" (to-sql-style (name (first item))) "> " (last item) " ")))
                          model-key-val)
        updated-content (-> (eval `(list->> ~content ~replace-key-pair))
                            (str/replace
                             (str "\"<" (to-sql-style model-name) "> ")
                             (str "\"<" (to-sql-style model-name) "> "  defmodel-desc " ")))]
    (jc1 db
         (->  (h/update :sqldots)
              (h/sset {:content updated-content})
              (h/where [:= :id id])))))

;; (create-enzh {:db conn :en_name "apple" :zh_name "苹果"})
(defn create-enzh [{:keys [db en_name zh_name]}]
  (try
    (jc1 db
         (->  (h/insert-into :enzhs)
              (h/values [{:en_name en_name
                          :zh_name zh_name}])
              (hp/upsert (-> (hp/on-conflict :en_name :zh_name)
                             (hp/do-nothing)))))
    (catch SQLException ex
      (prn "ERROR: duplicate key value violates unique constraint"))))

;; (get-enzh {:db conn :en_name "apple"})
(defn get-enzh [{:keys [db en_name]}]
  (jconn1 db
          (-> (h/select :*)
              (h/from :enzhs)
              (h/where [:= :en_name en_name]))))

;; 分别导入,以免conn连接太长
;; 1. (import-sql-dot-table conn "lib/his_graph.dot")
;; 2. (import-sql-dot-relation conn "lib/his_graph.dot")
;; 3. (updateall-sqldots-zh conn)
;; 4. (updateall-enzh conn)
(defn updateall-sqldots-zh
  [db]
  (for [{:keys [defmodel defmodel-desc model-key-val]} (get-all-defmodel db)]
    (do
      (prn (str "===>>> Update " defmodel " sqldots info"))
      (try
        (let [key-val (get-model-key-val model-key-val)]
          (do
            (update-sqldots-zh
             db
             defmodel
             defmodel-desc
             key-val)))
        (catch Throwable e
          (prn (str "Error!" defmodel ", " e)))))))

;; (def error-test (atom ""))
;; (for [item (get-model-key-val @error-test)] (prn item) )

(defn updateall-enzh
  [db]
  (for [{:keys [defmodel defmodel-desc model-key-val]} (get-all-defmodel db)]
    (do
      (prn (str "*******1111" defmodel "------" model-key-val))
      ;;(reset! error-test model-key-val)
      (let [key-val (get-model-key-val model-key-val)]
        (do
          (try
            (prn (str "*******1.1.1.1.1." (count key-val)))
            (for [item key-val]
              (try
                (prn (str "*******222222" item))
                (if (get-enzh {:db conn :en_name (name (first item))})
                  nil
                  (create-enzh {:db db :en_name (name (first item))
                                :zh_name (last item)}))
                (catch SQLException e
                  (prn (str "Error! create-enzh " e ", " (name (first item)))))
                (catch IndexOutOfBoundsException e
                  (prn (str "Error! create-enzh IndexOutOfBound " e)))
                (catch Exception e
                  (prn (str "Error! create-enzh Exception" e)))
                ))
            (catch IndexOutOfBoundsException e
              (prn (str "Error!  IndexOutOfBound " e)))
            ))))))

;; (racket/read-string-for-pro (fn [code-list file-name] (map first code-list)) "ydiff")
;; (import-racket-s-exp-to-blog conn "ydiff")
(defn import-racket-s-exp-to-blog
  [db & project]
  (let [content-fn
        (fn [content]
          (str "```racket\n"
               (pp/write content :dispatch pp/code-dispatch :stream nil)
               "\n```"))]
    (racket/read-string-for-pro
     (fn [code-list file-name]
       (do
         (prn (str file-name " >>>>>>"))
         (map
          (fn [content] (do (create-blog {:db db
                                          :name file-name
                                          :content (content-fn content)
                                          :project (first project)
                                          :source_type "SEMANTIC_SEARCH"})
                            (first content)))
          code-list)))
     (if project (first project) nil))))

;; (emacs/read-string-for-pro (fn [code-list file-name] (map first code-list)) "cider")
;; (import-emacs-s-exp-to-blog @conn "cider")
;; (count (jconn @conn (-> (h/select :id) (h/from :blogs) (h/where [:like :name "%emacs-jimw-code/cider%"])))) ;;=> 1776
;; (jconn @conn (-> (h/delete-from :blogs) (h/where [:like :name "%emacs-jimw-code/cider%"]) ))
(defn import-emacs-s-exp-to-blog
  [db & project]
  (let [content-fn
        (fn [content]
          (str "```elisp\n"
               (pp/write content :dispatch pp/code-dispatch :stream nil)
               "\n```"))]
    (emacs/read-string-for-pro
     (fn [code-list file-name]
       (do
         (prn (str file-name " >>>>>>"))
         (map
          (fn [content] (do
                          (info "+++++++" file-name)
                          (create-blog {:db db :name file-name
                                        :content (content-fn content)
                                        :source_type "SEMANTIC_SEARCH"
                                        :project (str (first project))})
                          file-name))
          code-list)))
     (if project (first project) nil))))

;; (scheme/read-string-for-pro (fn [code-list file-name] (map first code-list)) "AlgoXY")
;; (import-scheme-s-exp-to-blog @conn "AlgoXY")
;; (count (jconn @conn (-> (h/select :id) (h/from :blogs) (h/where [:like :name "%scheme-jimw-code/AlgoXY%"])))) ;; => 283
(defn import-scheme-s-exp-to-blog
  [db & project]
  (let [content-fn
        (fn [content]
          (str "```scheme\n"
               (pp/write content :dispatch pp/code-dispatch :stream nil)
               "\n```"))]
    (scheme/read-string-for-pro
     (fn [code-list file-name]
       (do
         (prn (str file-name " >>>>>>"))
         (map
          (fn [content] (do (create-blog {:db db
                                          :name file-name
                                          :content (content-fn content)
                                          :source_type "SEMANTIC_SEARCH"
                                          :project (first project)})
                            (first content)))
          code-list)))
     (if project (first project) nil))))

(defn export-todo-itemsets [db]
  (let [todo-itemsets (map
                       (fn [{:keys [content]}]
                         (str (clojure.string/join "," (map first (jieba-seg content))) "\n")
                         )
                       (jconn db
                              (-> (h/select :content)
                                  (h/from :todos))))]
    (with-open [wtr (clojure.java.io/writer
                     (str "resources/public/todo-itemsets.csv"))]
      (doseq [line todo-itemsets]
        (.write wtr line)))))

;; (map get-eng-name-word (-> (shell/sh "python2.7" "/home/clojure/jieba_cut.py" "我爱北京" "我爱北京天安门" "我爱Clojure") :out cjson/parse-string))
;; ["我爱北京", "我爱北京天安门", "我爱Clojure"] => (("北京") ("北京" "天安门") ("Clojure"))
(defn get-eng-name-word [items]
  (map
   first
   (filter
    (fn [x]
      (or (re-matches #"n(.*)" (last x))
          (= "eng" (last x)))) items)))

(defn export-jieba-name-todo-itemsets [db]
  (let [todo-list (map
                   (fn [{:keys [content]}] content)
                   (jconn db
                          (-> (h/select :content)
                              (h/from :todos))))
        todo-itemsets
        (map
         (fn [x]
           (str (clojure.string/join "," x)))
         (apply shell/sh (apply conj todo-list (list "python2.7" "/home/clojure/jieba_cut.py"))))]
    (with-open [wtr (clojure.java.io/writer
                     (str "resources/public/todo-itemsets-new.csv"))]
      (doseq [line todo-itemsets]
        (.write wtr line)))))

;; 生成todo list itemsets程序, (generate-todos-json-for-python conn) => (generate-todo-itemsets)
(defn generate-todos-json-for-python [db]
  (let [todo-json
        (cjson/generate-string
         (map
          (fn [{:keys [content]}] content)
          (jconn db
                 (-> (h/select :content)
                     (h/from :todos)))))]
    (with-open [wtr (clojure.java.io/writer
                     (str "todos.json"))]
      (.write wtr todo-json))))

;; ` cp /home/clojure/jimw-clj/bin/jieba_cut.py  /home/clojure/jieba_cut.py `
(defn generate-todo-itemsets []
  (let [name-eng-list
        (map
         (fn [x]
           (str (clojure.string/join "," x) "\n"))
         (map get-eng-name-word
              (-> (shell/sh "python2.7" "/home/clojure/jieba_cut.py")
                  :out cjson/parse-string)))]
    (with-open [wtr (clojure.java.io/writer
                     (str "todo-itemsets-new.csv"))]
      (doseq [line name-eng-list]
        (.write wtr line)))))

;;(-> (shell/sh "python2.7" "/home/clojure/jieba_cli.py" "clojure我爱"))

;; (-> (shell/sh "python2.7" "./bin/jieba_cli_clj.py" "clojure我爱" "我爱Lisp"))

;; 为什么repl用过了conn就会报这个错误呢?
;; java.lang.ClassCastException: clojure.lang.PersistentArrayMap cannot be cast to com.zaxxer.hikari.HikariDataSource, compiling:(jimw_clj/db/core.clj:79:1)
;;  DELETE FROM todos;
;;  DELETE FROM blogs;

(defn reimport-clj-project-s-exp
  [project-name]
  (do
    (jconn conn (-> (h/delete-from :blogs) (h/where [:like :name (str "%jimw-code/" project-name "%")])))
    (read-string-for-pro (fn [code-list file-name] (map first code-list)) project-name)
    (import-project-s-exp-to-blog conn project-name)
    (count (jconn conn (-> (h/select :id) (h/from :blogs) (h/where [:like :name (str "%jimw-code/" project-name "%")]))))))

;;(lite/start)
;;(lite/stop)
#_(with-conn [c @conn]
    (search-todos {:db c :q "a" :blog 40546})
    )
(defmacro with-conn
  [binding & body]
  `(clojure.java.jdbc/with-db-connection
     ~binding ~@body))

;; 灵感的消息中心: "当你对一个复杂问题没有任何启发的时候,用分布式来打败它,随时随地的灵感来聚合打败复杂问题"
;; (count (map (fn [x] [(:content x) (:blog x)]) (with-conn [c @conn] (search-todos-el {:db c :q "分布 打败"})))) ;;=> 6
;;;;;;; (["很难|的|问题|，|都|要|学会|分布式|去|打败|它" 5694] ...)
;; 1. 想法在于快速微分能力,快速λ化,在分布式的灵感的消息中,快速可微分
;; 2. 整体之于局部 => 以消息为中心的驱动相关的局部树形化,不管是哪篇文章的树: 比如有三篇不同领域的文章,todolist都说到"影响力",那这三篇文章的上下文影响力的部分都会被树形化
;; 3. 用上代码语义搜索的东西 => 这些局部树形化,就像一个lisp的闭包AST树一样(函数也是闭包),可以做"代码语义搜索", 搜索出来的结果是一颗颗局部树(向下遍历到最后点)
(defn search-todos-el [{:keys [db q]}]
  (let [res (insert-event {:db db :event_name "search-todos-el" :info "cli" :event_data q})]
    (jconn db
           (-> (h/select :t.id :t.blog :t.parid :t.content :t.done :t.sort_id :t.wctags :t.created_at :t.updated_at
                         :t.app_id :t.file :t.islast :t.percent :t.begin :t.mend :t.origin_content [:b.name :blog_title] [:b.source_type :blog_type])
               (h/from [:todos :t])
               (h/left-join [:blogs :b]
                            [:= :b.id :t.blog])
               (h/order-by [:t.id :desc])
               (h/where (when (seq q)
                          (let [q-list (clojure.string/split q #" ")]
                            (apply conj [:and]
                                   (map #(vector
                                          :like :t.content (str "%" % "%"))
                                        q-list)))))
               (h/limit 50)))))

;; 搜索搜索过的历史
;; (search-events-el {:db @conn :q "View"})
(defn search-events-el [{:keys [db q]}]
  (let [res (insert-event {:db db :event_name "search-events-el" :info "cli" :event_data q})]
    (jconn db
           (-> (h/select :*)
               (h/from :events)
               (h/where (when (seq q)
                          (let [q-list (clojure.string/split q #" ")]
                            (apply conj [:and]
                                   (map #(vector
                                          :like :event_data (str "%" % "%"))
                                        q-list)))))
               (h/limit 50)))))

;; (get-todo-frequencies {:db @conn :blog 5703})
;; 脑袋排除非名词:["成功" 5] ["找到" 5] ["能力" 4] ["时间" 3] ["影响" 3] ["货币" 3] ["有趣" 3]  ["人才" 3] ["东西" 3] ["产品" 3] ["模式" 2] ...
(defn get-todo-frequencies
  [{:keys [db q blog]}]
  (with-conn [c db]
    (sort-by
     #(* (last %) -1)
     (frequencies
      (flatten
       (map
        (fn [{:keys [content]}]
          (filter #(not (or (= % "|") (= % "。") (= % "？") (= % "?") (= % ".") (= % "，")))
                  (map first (jieba-wordcloud content))))
        (search-todos {:db c :q (if (nil? q) "" q) :blog blog})))))))

;; (jieba-wordcloud (:content (get-blog-by-id {:db @conn :id 5703})))
;; 脑袋排除非名词: ["产品" 18] ["公司" 17] [" 人才" 16]  ["想法" 12] ["电脑" 11] ...
(defn get-blog-by-id
  [{:keys [db id]}]
  (jconn1 db
          (-> (h/select :id :name :content :created_at
                        :updated_at :wctags :project :source_type
                        [(-> todos-subquery
                             (h/where [:= :blogs.id :todos.blog]))
                         :todos])
              (h/from :blogs)
              (h/where [:= :id id]))))

;; (get-voice-mark-content {:db @conn :id 5703})
;;  => ("传媒评论" "你必须做出难以两全的取舍" "你也会发现" "这里有个好点子" ...)
(defn get-voice-mark-content
  [{:keys [db id q]}]
  (with-conn [c db]
    (let [{:keys [content]} (get-blog-by-id {:db c :id id})]
      (map
       (fn [{:keys [begin mend]}]
         (if (nil? begin) ""
             (subs content begin mend)))
       (search-todos {:db c :q (if (nil? q) "" q) :blog id})))))

;; 输入几个关联路径(假设从标签云里面,选择拉线A->B,再拉线A->C): 如 `洗澡 -> 洗袜子`, `洗澡 -> 刷牙`

;; 如何分类出,与文章相关,还是与文章不相关的todos呢? 参考贝叶斯垃圾邮件分类: 用文章来训练分类器,用分类器来分类todos

(defn postwalk-add [stri {:keys [bind-key k-key]} ]
  (->
   (clojure.walk/postwalk
    #(cond
       ;;TYPE_A: `[a b c]`的参数类型处理
       (and (vector? %)
            (every? symbol? %)
            (some (fn [a] (= a (symbol k-key))) %))
       (vec (cons (symbol bind-key) %))
       ;;TYPE_B `{:aa 11}`的类型处理
       (and (map? %) (not (empty? %)))
       (if (= (count %) 1)
         %
         (merge (apply hash-map [(keyword bind-key) (symbol bind-key)]) %))
       ;;TYPE_C `h/select` & `h/returning`的类型处理
       (and (list? %) (or (= (first %) (symbol "h/select"))
                          (= (first %) (symbol "h/returning"))))
       (concat % (list (keyword bind-key)))
       ;;TYPE_N ....
       ;;TYPE_default
       :else %)
    (read-string stri))
   (clojure.pprint/write  :dispatch clojure.pprint/code-dispatch :stream nil)))

;; 从Viz的搜索做起: 可以进行大量的图搜索 => 我要的命令行todos的搜索不是片段,而是有相关信息的图
;; 1. 就像搜索代码的片段一样,可以返回,每个函数的完整部分,如何实现的: 直接用(()(()))一样的东西来文本型的表达树就好 => 用lisp来表示todos树
;; 2. 如何让todos的viz树变得像Lisp一样,机器可解释呢? => 机器学习的内容了
;; 3. 构建一个自我的知识图谱,作为todos的解释器,广义的SICP的解释器 => 基于知识的todos语言


#_(java/import-project-file-to-blog
   @conn
   create-blog "xunfeiyuji_197-enjarify.jar.src"
   (honeysql.core/call :cast "REVERSE_ENGINEERING" :SOURCE_TYPE))

;; 要去掉回车:
;;(slurp "lib/books/danbook/151.txt")
;;=> " \n\n \n\n \n\n’} ,\nI\n3275.88 元, 收益率为 3.275%。 V '\n在卖出时, 赚钱的交易有1笔。\n\n \n\n"

;;(str/replace (slurp "lib/books/danbook/151.txt") "\n" "")

;;(range 220)

;; TODO: 如何把不超过4000字的多页放在一篇文章里? [(list 1000 1000 500 1500) (list 500 1500 1000 1000)] ..]

;; 1. 暴力办法: 全部的文本join到一起,然后partition-by (> 4000 (count x)) => 然后找出它对应的页数是谁,通过开始和结束的标记来确定是哪一页
;; 2. 建立在第一步基础上,如果加了本页,超过了4000字,那么就不加入本页
(defn add-start-end-page [content page]
  (str "☛" page content page "☚"))

;;(count book-str) ;;(count book-str) => 21245
#_(def book-str
    (str/join 
     (map
      (fn [page]
        (add-start-end-page (str/replace (slurp (str "lib/books/danbook/" page ".txt")) "\n" "") page)
        )
      (range 22))))

;;(partition-by #(= (count %) 4000)  book-str)
;; count not supported on this type: Character

;; (split-with #(= (count %) 4000)  book-str)
;;    count not supported on this type: Character

;; (create-books {:db @conn :book-name "测试书-" :book-path "danbook" :range-nth 220})
(defn create-books
  [{:keys [db book-name book-path range-nth]}]
  (for [page (range range-nth)]
    (let [{:keys [id]} (create-blog {:db db :name (str book-name page)
                                     :content
                                     (str/replace
                                      (slurp (str "lib/books/" book-path "/" page ".txt"))
                                      "\n\n" "")
                                     :source_type "BOOK_OCR"})]
      (create-todo {:db db :content "root" :parid 1 :blog id}))))

(defn get-all-source
  [{:keys [db]}]
  (:enum_range
   (jconn1
    db
    (h/select (honeysql.core/raw "enum_range(NULL::source_type)")))))

(defn get-all-project
  [{:keys [db]}]
  (->>
   (jconn
    db
    (-> 
     (h/select
      (honeysql.core/raw "DISTINCT project"))
     (h/from :blogs)))
   (map :project)
   (filter string?)))

(defn add-s-exp-commit
  [{:keys [db]} {:keys [s_exp_info_before eval_result_before s_exp_info_after eval_result_after commit_info author s_exp_file_name done]} _]
  (jc1 db (-> (h/insert-into :s-exp-commit)
              (h/values [{:s_exp_info_before s_exp_info_before
                          :eval_result_before eval_result_before
                          :s_exp_info_after s_exp_info_after
                          :eval_result_after eval_result_after
                          :commit_info commit_info
                          :author author
                          :s_exp_file_name s_exp_file_name
                          :done done
                          :created_at (sql/call :now)}]))))

(defn update-s-exp-commit
  [{:keys [db]} {:keys [id s_exp_info_before eval_result_before s_exp_info_after eval_result_after commit_info author s_exp_file_name done created_at updated_at]} _]
  (jc1 db (-> (h/update :s-exp-commit)
              (h/sset (->> {:s_exp_info_before s_exp_info_before
                            :eval_result_before eval_result_before
                            :s_exp_info_after s_exp_info_after
                            :eval_result_after eval_result_after
                            :commit_info commit_info
                            :author author
                            :s_exp_file_name s_exp_file_name
                            :done done
                            :updated_at (sql/call :now)}
                           (filter
                            #(not (nil? (last %))))
                           (into {})))
              (h/where [:= :id id]))))

;; 9, 11, 20, 21, 23 是错误的
;; (for-import-pdf {:pdf-file "Lecun98.pdf" :op-fn (fn [num content] (prn (str num "------" content))) :error-fn prn})
(defn for-import-pdf [{:keys [pdf-file op-fn error-fn]}]
  (let [pdf-count (info/page-number pdf-file)]
    (for [num (range pdf-count)]
      (do
        (prn (str "==========" (inc num)))
        (try
          (-> (split/split-pdf :input pdf-file :start (inc num) :end (inc num))
              first
              text/extract
              ((fn [content]
                 (op-fn (inc num) content))))
          (catch Exception e
            (error-fn (inc num) (str e) )
            (prn (str (inc num) " ******* Error! " e)))
          ))
      )))

;; (import-pdf-to-blog {:db @conn :pdf-file "Lecun98.pdf"})
(defn import-pdf-to-blog [{:keys [db pdf-file]}]
  (for-import-pdf
   {:pdf-file pdf-file
    :error-fn (fn [num content]
                (create-blog {:db db :name (str pdf-file ", 第" num "页")
                              :content content
                              :source_type "ENG_PDF_OCR"
                              :project pdf-file}))
    :op-fn (fn [num content]
             (create-blog {:db db :name (str pdf-file ", 第" num "页")
                           :content content
                           :source_type "ENG_PDF_OCR"
                           :project pdf-file}))}))

;; 解决如下错误: org.postgresql.util.PSQLException: ERROR: invalid byte sequence for encoding "UTF8": 0x00
;; (invalid-byte-for-pdf "Lecun98.pdf" (list 1 32 37))
(defn invalid-byte-for-pdf [pdf-file ids]
  (for [page-num ids]
    (-> (split/split-pdf :input pdf-file :start page-num :end page-num)
        first
        text/extract
        ((fn [content]
           (with-open [wtr (clojure.java.io/writer (str pdf-file page-num ".txt"))]
             (.write wtr content)
             )
           )
         )
        )
    )
  )

;; 解决如下错误: java.io.IOException: COSStream has been closed and cannot be read. Perhaps its enclosing PDDocument has been closed?
;; (pdf-to-text "Lecun98.pdf")
(defn pdf-to-text [pdf-file]
  (with-open [wtr (clojure.java.io/writer (str pdf-file ".txt"))]
    (.write wtr (text/extract pdf-file))))

;; (generate-qrcode "Hi, Steve Chan!" "stevechan_test.png")
(defn generate-qrcode [content output-file]
  (with-open [out (clojure.java.io/output-stream
                   (str "resources/public/qrcode/" output-file))]
    (clojure.java.io/copy (.file (QRCode/from content)) out)))

;; (get-blog-root-todo-id {:db @conn :blog 57921}) ;;=> {:todo-root-id 306, :blog-id 57921}
(defn get-blog-root-todo-id
  [{:keys [db blog]}]
  (let [{:keys [id]}
        (jconn1 db
                (-> (h/select :id)
                    (h/from :todos)
                    (h/where [:= :blog blog])))]
    {:todo-root-id id :blog-id blog}))

;; (add-search-event-for-blog {:db @conn :blog 57921 :eid 283})
;; 搜索的时候,目中目标可以绑定search的event的id
(defn add-search-event-for-blog
  [{:keys [db blog eid]}]
  (jc1 db
       (-> (h/update :blogs)
           (h/sset
            {:search_events
             (sql/call :array_cat :search_events
                       (honeysql.types/array [eid]))})
           (h/where [:= :id blog]))))

;; 创世的时候,假设只有10个单词,每个单词都有独特的编号
;; A: where B: json_column R: json_column

(def a-vec "where") ;; 头实体
(def b-vec "json_column") ;; 尾实体: 含有json属性的字段才是符合要求的向量

(def r-vec "select_condition") ;; 代码语义关系: 查询条件关系

;;=> (= (+ a-vec r-vec) b-vec) 

;; 已经有的图谱的样子: ["where" "select_condition" "json_a"] ["where" "select_condition" "json_b"] ... , json_a, json_b都有json的维度属性

;;;; TODO: 如何归纳代码节点(实体)之间的关系?
;; 只有用代码知识图谱(N个三元组)来表达了一个S代码,才能用语义来搜索一个代码
;; 节点或者是实体都已经有了: 缺乏的就是关系的提取了
;; 要想很好的演绎,就必须先很好的归纳成多维的向量
;; 根据已经有的代码来归纳它们的维度属性(如字段meta是json属性), 上下文属性等

;; Elisp: (get-sql-table-cols nil 'add-table-jimw)
(defn add-pcmip
  [{:keys [db]} {:keys [ipaddress]} _]
  (jc1 db (-> (h/insert-into :pcmip)
              (h/values [{:ipaddress ipaddress
                          :created_at (sql/call :now)}]))))

(defn add-s-exp-vector
  [{:keys [db]} {:keys [blog content project]} _]
  (jc1 db (-> (h/insert-into :s-exp-vector)
              (h/values [{:blog blog
                          :project project
                          :content content}]))))

(defn update-s-exp-vector
  [{:keys [db]} {:keys [id blog content project]} _]
  (jc1 db (-> (h/update :s-exp-vector)
              (h/sset (->> {:blog blog
                            :content content
                            :project project
                            :updated_at (sql/call :now)}
                           (filter
                            #(not (nil? (last %))))
                           (into {})))
              (h/where [:= :id id]))))

;; (import-project-s-vector {:db @conn :project "cider"})
(defn import-project-s-vector
  [{:keys [db project]}]
  (let [res (jconn db (-> (h/select :*)
                          (h/from :blogs)
                          (h/merge-where [:= :project project])))]
    (for [item res]
      (clojure.walk/postwalk
       #(if (coll? %)
          (do
            (with-conn [c db]
              (add-s-exp-vector {:db c} {:blog (:id item)
                                         :content (str %)
                                         :project project} {}))
            (prn %)
            ;;返回空字符串给seq
            "")
          %)
       (read-string
        (-> (:content item)
            (str/replace "```clojure\n" "")
            (str/replace "\n```" ""))))  
      )
    )
  )

#_(try (add-special-form {:db @conn} {:content "if"} {})
       (catch SQLException ex (prn ex)))
(defn add-special-form
  [{:keys [db]} {:keys [content]} _]
  (jc1 db (-> (h/insert-into :special-form)
              (h/values [{:content content
                          :created_at (sql/call :now)}]))))

(defn update-special-form
  [{:keys [db]} {:keys [id content]} _]
  (jc1 db (-> (h/update :special-form)
              (h/sset (->> {:content content
                            :updated_at (sql/call :now)}
                           (filter
                            #(not (nil? (last %))))
                           (into {})))
              (h/where [:= :id id]))))

;; (import-project-special-form {:db @conn :project "cider"})
(defn import-project-special-form
  [{:keys [db project]}]
  (let [res (jconn db (-> (h/select :*)
                          (h/from :s-exp-vector)
                          (h/merge-where [:= :project project])))]
    (for [item res]
      (do
        (let [s-exp (read-string (:content item))]
          (if (and (list? s-exp) (symbol? (first s-exp)))
            (try
              (with-conn [c db]
                (add-special-form {:db c}
                                  {:content (str (first s-exp))} {}))
              (catch SQLException ex (prn ex)))
            nil))))))

;; (two-keyword-relationship {:neo4j-conn @neo4j-conn :kw1 "where" :kw2 "json_column" :rel-name "argument_relationship" :rel-zh "参数关系"})
;; => #clojurewerkz.neocons.rest.records.Relationship{:id 2, :location-uri "http://localhost:7474/db/data/relationship/2", :start "http://localhost:7474/db/data/node/64", :end "http://localhost:7474/db/data/node/65", :type "argument_relationship", :data {:source "参数关系"}}
(defn two-keyword-relationship
  [{:keys [neo4j-conn kw1 kw2 rel-name rel-zh]}]
  (let [keyword1 (nn/create neo4j-conn {:keyword kw1})
        keyword2 (nn/create neo4j-conn {:keyword kw2})
        rel   (nrl/create neo4j-conn keyword1 keyword2
                          (keyword rel-name)
                          {:source rel-zh})]
    (nrl/get neo4j-conn (:id rel))))

(defn add-s-exp-history
  [{:keys [db]} {:keys [in_put out_put buffer_name]} _]
  (jc1 db (-> (h/insert-into :s-exp-history)
              (h/values [{:in_put in_put
                          :out_put out_put
                          :buffer_name buffer_name
                          :created_at (sql/call :now)}]))))

(defn update-s-exp-history
  [{:keys [db]} {:keys [id in_put out_put buffer_name]} _]
  (jc1 db (-> (h/update :s-exp-history)
              (h/sset (->> {:in_put in_put
                            :out_put out_put
                            :buffer_name buffer_name
                            :updated_at (sql/call :now)}
                           (filter
                            #(not (nil? (last %))))
                           (into {})))
              (h/where [:= :id id]))))

(defn import-md-books []
  (for [title (list "2016-12-02-Chapter2_linear_algebra.md"
                    "2016-12-03-Chapter3_probability_and_information_theory.md"
                    "2016-12-04-Chapter4_numerical_computation.md"
                    "2016-12-05-Chapter5_machine_learning_basics.md"
                    "2016-12-06-Chapter6_deep_feedforward_networks.md"
                    "2016-12-07-Chapter7_regularization.md"
                    "2016-12-08-Chapter8_optimization_for_training_deep_models.md"
                    "2016-12-09-Chapter9_convolutional_networks.md"
                    "2016-12-10-Chapter10_sequence_modeling_rnn.md"
                    "2016-12-11-Chapter11_practical_methodology.md"
                    "2016-12-12-Chapter12_applications.md"
                    "2016-12-13-Chapter13_linear_factor_models.md"
                    "2016-12-14-Chapter14_autoencoders.md"
                    "2016-12-15-Chapter15_representation_learning.md"
                    "2016-12-16-Chapter16_structured_probabilistic_modelling.md"
                    "2016-12-17-Chapter17_monte_carlo_methods.md"
                    "2016-12-18-Chapter18_confronting_the_partition_function.md"
                    "2016-12-19-Chapter19_approximate_inference.md"
                    "2016-12-20-Chapter20_deep_generative_models.md")]
    (create-blog {:db @conn :name (str "深度学习~>" title)
                  :content (slurp title) :source_type "WEB_ARTICLE"})))

(defn for-update-blog-source
  [db]
  (doseq [blog (jconn db (-> (h/select :*) (h/from :blogs)
                             (h/where [:like :name "%matlab-jimw-code%"])
                             (h/merge-where [:= :source_type (honeysql.core/call :cast  "BLOG" :SOURCE_TYPE)])))]
    (with-conn [c db]
      (update-blog {:db c :id (:id blog) :source_type "SEMANTIC_SEARCH" :project "PRMLT_MATLAB"}))))

;; (parse-google-pdf-translate-html "test.htm")
(defn parse-google-pdf-translate-html
  [file]
  (->
   (hs/select
    (hs/class "notranslate")
    (->
     (slurp file)
     hickory/parse
     hickory/as-hickory))))

(defn fix-blog-time-bug []
  (for [{:keys [id]}
        (jconn @conn
               (-> (h/select :id)
                   (h/from :blogs)
                   (h/merge-where [:= :updated_at nil])))]
    (with-conn [c @conn]
      (update-blog-updated-time {:db @conn :blog id}))))

(defn map-set-color
  [{:keys [content split-ids]}]
  (let [set-color (fn [x] (str "<span style='color: rgb(255, 0, 0);'>" x "</span>"))]
    (str/join
     ""
     (map-indexed
      (fn [idx item]
        (let [begin (first item)
              mend (last item)]
          (cond (= idx 0)
                (str (subs content 0 begin)
                     (set-color (subs content begin mend)))
                (= idx (- (count split-ids) 1))
                (str (subs content (last (nth split-ids (- idx 1))) begin)
                     (set-color (subs content begin mend))
                     (subs content mend (count content)))
                :else (str (subs content (last (nth split-ids (- idx 1))) begin)
                           (set-color (subs content begin mend)))
                ))
        ) split-ids))
    )
  )

(comment
  (let [unit (JavaParser/parse "class A { }")]
    (.getClassByName unit "A")
    )
  )

