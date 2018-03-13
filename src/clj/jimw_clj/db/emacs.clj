(ns jimw-clj.db.emacs
  (:require
   [clojure.string :as str]
   [clojure.java.shell :as shell]
   [clojure.pprint :as pp]
   [taoensso.timbre :refer [error debug info]]
   [instaparse.core :as insta]))


;; (read-string (str "(" (slurp "lib/emacs-jimw-code/cider/cider-client.el") ")"))

