(ns jimw-clj.db.cpp
  (:require
   [clojure.string :as str]
   [clojure.java.shell :as shell]
   [clojure.pprint :as pp]
   [taoensso.timbre :refer [error debug info]]))

(def cpp-ast-eg (read-string
                 (str/replace
                  (slurp "examples/test-parse-cpp1.rkt")
                  "#f" "sharp-function")))

(defn plist?
  [li]
  (= clojure.lang.PersistentList (type li)))

(defn remove-not-need-cpp-ast-item
  [lis]
  (remove
   #(or (= % (symbol "sharp-function"))
        (number? %)
        (= % (symbol "Node"))) lis))

;; (slim-ydiff-cpp-ast cpp-ast-eg #(print %))
(defn slim-ydiff-cpp-ast
  [cpp-ast op-fn]
  (let [code (clojure.walk/postwalk
              #(if (coll? %)
                 (if (plist? %) (remove-not-need-cpp-ast-item %) (vector (last %)))
                 %) cpp-ast)]
    (op-fn (pp/write code :dispatch pp/code-dispatch :stream nil))))
