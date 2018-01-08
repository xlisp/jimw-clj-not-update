(ns jimw-clj.db.scheme
  (:require
   [clojure.string :as str]
   [clojure.java.shell :as shell]
   [clojure.pprint :as pp]
   [taoensso.timbre :refer [error debug info]]))

(defn remove-invalid-token [st]
  (-> st
      (str/replace #"(?s)[#\|]+(.*?)\|#" "")))

(def scheme-s-list-eg
  (read-string
   (str "(list "
        (->>
         (slurp "lib/scheme-jimw-code/AlgoXY/datastruct/elementary/queue/src/lazy-queue.scm")
         remove-invalid-token) ")")))
