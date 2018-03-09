(ns jimw-clj.db.streaming
  (:require
   [clojure.core.async :as a :refer [<! >! <!! >!!]]
   [me.raynes.conch.low-level :as sh]
   [taoensso.timbre :refer [info error debug trace report fatal]]
   [cheshire.core :as json]
   [clojure.java.io :as io]
   [mount.lite :as lite]
   [clojure.string :as str]
   [jimw-clj.sente :as sente]
   [jimw-clj.config :as config :refer [env]]))

(defn- make-streaming-proc []
  (let [{:keys [username password database-name slot pg-recvlogical]}
        (:datasource-options @config/jimw-conf)]
    (let [env {"PGHOST"     "localhost"
               "PGPORT"     "5432"
               "PGDATABASE" database-name
               "PGUSER"     username
               "PGPASSWORD" password}]
      (sh/proc pg-recvlogical
               "--no-loop"
               "--dbname" database-name
               "--slot" slot
               "--start" "--file" "-"
               :env env))))

(lite/defstate proc
  :start (if (= (get (System/getenv) "OS_TYPE") "MACOX")
           (sh/proc "bin/pg_stream.sh")
           (make-streaming-proc))
  :stop 111)

(def stream-source (a/chan 10))

(defn data-stream []
  (a/thread
    (with-open [rdr (io/reader (:out @proc))]
      (doseq [item (json/parsed-seq rdr true)]
        (>!! stream-source item)))))

;; (data-stream) ;; => #object[clojure.core.async.impl.channels.ManyToManyChannel 0x6bca2a18 "clojure.core.async.impl.channels.ManyToManyChannel@6bca2a18"]

;; jim0: 只能监听一次的DB变化
;; (prn (<!! stream-source))

;; jim1: 监听多次的DB变化 => 不可终止的循环,只能留在里面,即内存泄露了
#_(a/go-loop []
    (let [x (<!! stream-source)]
      (println "Got a value in this loop:" x))
    (recur))

;; jim2: 监听多次的DB变化, 可以终止的循环
#_(def aaa
    (let [stop-ch (a/promise-chan)]
      (a/go-loop []
        (let [[v port] (a/alts! [stop-ch stream-source] :priority true)]
          (when-not (= port stop-ch)
            (println "Message: " v "\nFrom Object: " port)
            )
          )
        #_(let [x (<!! stream-source)]
            (println "Got a value in this loop:" x))
        (recur))
      stop-ch))
;; =>
;; Message:  {:change [{:kind update, :schema public, :table blogs, ... }
;; From Object:  #object[clojure.core.async.impl.channels.ManyToManyChannel...]

;; 终止go-loop循环
;; (a/close! aaa) ;; => nil

(lite/defstate pg-streaming-change
  :start
  (let [start_stream (data-stream)
        stop-ch (a/promise-chan)]
    (a/go-loop []
      (let [[v port] (a/alts! [stop-ch stream-source] :priority true)]
        (when-not (= port stop-ch)
          (doseq [uid (:any @(:connected-uids sente/sente))]
            (do
              (info "===>>>>" v) 
              ((:send-fn sente/sente)
               uid
               [:msg/push-all {:msgs (json/generate-string v)}])
              )
            )          
          )
        )
      (recur))
    stop-ch)
  :stop 2222)


(def test-stream-update-data
  {:xid 1740, ;; 每一个数据流变更事件都是唯一的交易id: xid
   :change [{:kind "update", :schema "public", :table "todos", :columnnames
             ["id" "blog" "parid" "content" "created_at" "updated_at" "done" "sort_id" "wctags" "app_id" "file" "islast" "percent" "begin" "mend" "origin_content"],
             :columntypes ["int8" "int8" "int8" "text" "timestamptz" "timestamptz" "bool" "int4" "jsonb" "int4" "text" "bool" "int4" "int4" "int4" "text"],
             :columnvalues [9702 5727 9699 "Emacs也是同步更新的,操作生成语义树" "2018-03-08 15:46:45.336272-05" "2018-03-08 15:58:40.667049-05" false 5782833 "{}" nil nil nil nil nil nil nil],
             :oldkeys {:keynames ["id"], :keytypes ["int8"], :keyvalues [9702]}}]})

(def test-stream-insert-data
  {:xid 1743,
   :change [{:kind "insert", :schema "public", :table "todos", :columnnames
             ["id" "blog" "parid" "content" "created_at" "updated_at" "done" "sort_id" "wctags" "app_id" "file" "islast" "percent" "begin" "mend" "origin_content"],
             :columntypes ["int8" "int8" "int8" "text" "timestamptz" "timestamptz" "bool" "int4" "jsonb" "int4" "text" "bool" "int4" "int4" "int4" "text"],
             :columnvalues [9704 5727 9702 "这点很重要,可以整合cider和jimw-clj的语义搜索的能量(静态分析)" "2018-03-08 16:05:08.441853-05" "2018-03-08 16:05:08.441853-05" false 5784109 "{}" nil nil nil nil nil nil nil]}]})

;; 没有Websocket,后端是操作不了前端的=>> 后端只是把JSON消息发给前端,让它自己去处理吧
#_(let [change (first (:change test-stream-update-data))
        {:keys [kind table columnnames columnvalues]} change
        {:keys [id blog parid content created_at updated_at done
                sort_id wctags app_id file islast percent begin mend origin_content]}
        (zipmap (map keyword columnnames) columnvalues)]
    )

