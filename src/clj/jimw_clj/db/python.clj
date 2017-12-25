(ns jimw-clj.db.python
  (:require
   [clojure.string :as str]
   [clojure.java.shell :as shell]
   [clojure.pprint :as pp]
   [taoensso.timbre :refer [error debug info]]))

(defn is-py-def
  [node]
  (= (symbol "|py-FunctionDef|") (first node)))

;; (def python-ast (read-string (slurp "lib/python-jimw-code/tensorflow/third_party/examples/eager/spinn/spinn.py.ast")))

;; (import-python-def-and-other python-ast "file-name" #(print %) #(print %))
(defn import-python-def-and-other
  [python-ast file-name save-def-fn save-other-fn]
  (let [res (clojure.walk/postwalk
             #(if (coll? %)
                (do
                  (if (is-py-def %)
                    (do
                      (save-def-fn (pp/write % :dispatch pp/code-dispatch :stream nil))
                      (list :def-python-function (second %)))
                    %))  %) python-ast)]
    (save-other-fn
     (pp/write res :dispatch pp/code-dispatch :stream nil))))
