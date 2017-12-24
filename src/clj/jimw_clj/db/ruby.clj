(ns jimw-clj.db.ruby
  (:require
   [clojure.string :as str]
   [clojure.java.shell :as shell]
   [taoensso.timbre :refer [error debug info]]))

(defn remove-invalid-token [st]
  (-> st
      (str/replace #":@" ":at-rb")
      (str/replace #":\[\]" ":arra-rb")
      (str/replace #":\"(.*)\"" ":rb-symst-$1")
      (str/replace ":*" ":rbstar")
      (str/replace ":<<" ":rbleftleft")
      (str/replace ":%" ":rbbaifen")))

(defn is-rb-def
  [node]
  (= (symbol "def") (first node)))

;; (read-string-for-pro (fn [code-list file-name] (map first code-list)) "rails")
(defn read-string-for-pro
  [op-fn & project]
  (let [file-names
        (->>
         (->
          (shell/sh "find"
                    (if project
                      (str "lib/ruby-jimw-code/" (first project))
                      "lib") "-name" "*.ast") :out
          (clojure.string/split #"\n")))
        split-code
        (fn [file-name]
          (let [_ (prn (str file-name " >>>>>>"))
                code-list
                (try
                  (read-string (remove-invalid-token (slurp file-name)))
                  (catch Exception e ()))]
            (op-fn code-list file-name)))]
    (for [file-name file-names]
      (split-code file-name))))
