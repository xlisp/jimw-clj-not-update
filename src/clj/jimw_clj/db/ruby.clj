(ns jimw-clj.db.ruby
  (:require
   [clojure.string :as str]
   [clojure.java.shell :as shell]
   [clojure.pprint :as pp]
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

;; (import-ruby-def-and-other ruby-ast "file-name" #(print %) #(print %))
(defn import-ruby-def-and-other
  [ruby-ast file-name save-def-fn save-other-fn]
  (let [res (clojure.walk/postwalk
             #(if (coll? %)
                (do
                  (if (is-rb-def %)
                    (do
                      (save-def-fn (pp/write % :dispatch pp/code-dispatch :stream nil))
                      (list :def-ruby-function (second %)))
                    %))  %) ruby-ast)]
    (save-other-fn
     (pp/write res :dispatch pp/code-dispatch :stream nil))))
