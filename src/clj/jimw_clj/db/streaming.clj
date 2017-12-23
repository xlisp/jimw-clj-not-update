(ns jimw-clj.db.streaming
  (:require
   [clojure.core.async :as a :refer [<! >! <!! >!!]]
   [me.raynes.conch.low-level :as sh]
   [taoensso.timbre :refer [info error debug trace report fatal]]
   [cheshire.core :as json]
   [clojure.java.io :as io]
   [clojure.string :as str]))

(defn- make-streaming-proc []
  (let [env {"PGHOST"     "localhost"
             "PGPORT"     "5432"
             "PGDATABASE" "blackberry"
             "PGUSER"     "postgres"
             "PGPASSWORD" "123456"}]
    (sh/proc "pg_recvlogical"
             "--no-loop"
             "--dbname" "blackberry"
             "--slot" "blackberry_streaming"
             "--start" "--file" "-"
             :env env)))

(def proc (make-streaming-proc))

(def stream-source (a/chan 10))

(defn data-stream []
  (a/thread
    (with-open [rdr (io/reader (:out proc))]
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
