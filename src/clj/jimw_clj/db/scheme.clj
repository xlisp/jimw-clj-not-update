(ns jimw-clj.db.scheme
  (:require
   [clojure.string :as str]
   [clojure.java.shell :as shell]
   [clojure.pprint :as pp]
   [taoensso.timbre :refer [error debug info]]))

(defn remove-invalid-token [st]
  (-> st
      (str/replace #"(?s)[#\|]+(.*?)\|#" "")
      (str/replace "#f" "sharp-function")))

;; (scheme-s-list-eg "lib/scheme-jimw-code/AlgoXY/datastruct/elementary/queue/src/lazy-queue.scm")
(defn scheme-s-list-eg [file]
  (read-string
   (str "(list "
        (->>
         (slurp file)
         remove-invalid-token) ")")))

;; (read-string-for-pro (fn [code-list file-name] (map first code-list)) "AlgoXY")
(defn read-string-for-pro
  [op-fn & project]
  (let [file-names
        (->>
         (->
          (shell/sh "find"
                    (if project
                      (str "lib/scheme-jimw-code/" (first project))
                      "lib") "-name" "*.ss") :out
          (clojure.string/split #"\n")))
        split-code
        (fn [file-name]
          (let [_ (prn (str file-name " >>>>>>"))
                list-init (fn [st] (str "( " st " )"))
                code-list (->>
                           (slurp file-name)
                           remove-invalid-token
                           list-init
                           read-string)]
            (op-fn code-list file-name)))]
    (for [file-name file-names]
      (split-code file-name))))
