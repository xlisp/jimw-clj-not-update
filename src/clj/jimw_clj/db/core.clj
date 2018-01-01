(ns jimw-clj.db.core
  (:require
    [cheshire.core :refer [generate-string parse-string]]
    [clojure.java.jdbc :as jdbc]
    [conman.core :as conman]
    [jimw-clj.config :refer [env]]
    [mount.core :refer [defstate]]
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
    [clj-jri.R :as R]
    [cheshire.core :as cjson]
    [clojure.core.async :as async]
    [jimw-clj.db.scheme :as scheme])
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
           [com.huaban.analysis.jieba JiebaSegmenter$SegMode]))

(def segmenter (JiebaSegmenter.))

;; (jieba-seg "这是一个伸手不见五指的黑夜。我叫Steve，我爱北京")
;; => [[这是 0 2] [一个 2 4] [伸手不见五指 4 10] [的 10 11] [黑夜 11 13] [。 13 14] [我 14 15] [叫 15 16] [steve 16 21] [， 21 22] [我 22 23] [爱 23 24] [北京 24 26]]
(defn jieba-seg [st]
  (->
   segmenter
   (.process st JiebaSegmenter$SegMode/SEARCH)
   .toString
   read-string))

;; (jieba-wordcloud "这是一个伸手不见五指的黑夜。我叫Steve，我爱北京, 我爱Clojure")
;; => ([我 3] [爱 2] [叫 1] [伸手不见五指 1] [27 1] [五指 1] [steve 1] [这是 1] [一个 1] [。 1] [黑夜 1] [北京 1] [不见 1] [clojure 1] [， 1] [26 1] [伸手 1] [的 1])
(defn jieba-wordcloud [st]
  (->>
   (->
    segmenter
    (.process st JiebaSegmenter$SegMode/INDEX)
    .toString
    read-string)
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

;; load r lib
(if (R/eval r-lib) (info "load R lib ok...") (throw (Exception. "load R lib failure !")))

(def get-term-matrix-path (R/eval "paste(getwd(),'/src/R/getTermMatrix.R', sep='')"))

;; load getTermMatrix function
(R/eval (str "source('" get-term-matrix-path "')"))

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

(defn get-blog-wctags [{:keys [db id]}]
  (jconn1 db
          (-> (h/select :id :wctags)
              (h/from :blogs)
              (h/where [:= :id id]))))

;; (update-blog {:db conn :id 5000 :name nil :content "dasdsdas"})
(defn update-blog [{:keys [db id name content]}]
  (let [res (jc1 db (->  (h/update :blogs)
                         (h/sset (->> {:name    (when (seq name) name)
                                       :content (when (seq content) content)
                                       :updated_at (honeysql.core/call :now)}
                                      (remove (fn [x]  (nil? (last x))))
                                      (into {})))
                         (h/where [:= :id id])))
        update-wctags (fn []
                        (let [res-json (R/eval
                                        (str "toJSON(getTermMatrix(\""
                                             (->>
                                              (clojure.string/split (:content res) #"\W")
                                              (remove #(= % ""))
                                              (clojure.string/join " ")) "\"))"))]
                          (info (str "JSON:" res-json "========"))
                          (jc1 conn
                               (->  (h/update :blogs)
                                    (h/sset {:wctags (sql/call
                                                      :cast
                                                      (cjson/generate-string
                                                       (cjson/parse-string
                                                        res-json)) :jsonb)})
                                    (h/where [:= :id (:id res)])))))]
    (async/go
      (async/<! (async/timeout (* 2 1000)))
      (update-wctags))    
    res))

;; (create-blog {:db conn :name "测试" :content "aaaaabbbccc"})
(defn create-blog [{:keys [db name content]}]
  (let [res (jc1 db (->  (h/insert-into :blogs)
                         (h/values [{:name name
                                     :content content}])))]
    res))

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
  [db]
  (let [file-names
        (->
         (shell/sh "find" "lib" "-name" "*.clj*")
         :out
         (clojure.string/split #"\n"))
        content-fn (fn [file-name] (str "```clojure\n" (slurp file-name) "\n```"))]
    (for [file-name file-names]
      (create-blog {:db db :name file-name :content (content-fn file-name)}))))

;; 测试新的项目导入是否解析报错:
;; (read-string-for-pro (fn [code-list file-name] (map first code-list)) "leiningen")
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
          (fn [content] (do (create-blog {:db db :name file-name :content (content-fn content)}) (first content)))
          code-list)))
     (if project (first project) nil))))

(defn insert-event [{:keys [db event_name info event_data]}]
  (jc1 db
       (-> (h/insert-into :events)
           (h/values [{:event_name event_name
                       :info       (when (seq info) info)
                       :event_data (when (seq event_data) event_data)}]))))

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

;; (scheme/read-string-for-pro (fn [code-list file-name] (map first code-list)) "ydiff")
;; (import-cpp-s-exp-to-blog conn "ydiff")
(defn import-cpp-s-exp-to-blog
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
          (fn [content] (do (create-blog {:db db :name file-name :content (content-fn content)}) (first content)))
          code-list)))
     (if project (first project) nil))))
