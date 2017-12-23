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

;; 只能监听一次的DB变化
;; (prn (<!! stream-source))
