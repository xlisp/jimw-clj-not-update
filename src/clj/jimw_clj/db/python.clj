(ns jimw-clj.db.python
  (:require
   [clojure.string :as str]
   [clojure.java.shell :as shell]
   [clojure.pprint :as pp]
   [taoensso.timbre :refer [error debug info]]))

#_(read-string (slurp "lib/python-jimw-code/tensorflow/third_party/examples/eager/spinn/spinn.py.ast"))
