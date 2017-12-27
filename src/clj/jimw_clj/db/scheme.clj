(ns jimw-clj.db.scheme
  (:require
   [clojure.string :as str]
   [clojure.java.shell :as shell]
   [clojure.pprint :as pp]
   [taoensso.timbre :refer [error debug info]]))

;; 测试新的项目导入是否解析报错:
;; (read-string-for-pro (fn [code-list file-name] (map first code-list)) "ydiff")
(defn read-string-for-pro
  [op-fn & project]
  (let [file-names
        (->>
         (->
          (shell/sh "find"
                    (if project
                      (str "lib/scheme-jimw-code/" (first project))
                      "lib") "-name" "*.rkt") :out
          (clojure.string/split #"\n")))
        split-code
        (fn [file-name]
          (let [_ (prn (str file-name " >>>>>>"))
                remove-invalid-token
                (fn [st]
                  (-> st
                      (str/replace "i) #\\/)" "i) -back-slant28-que-")
                      (str/replace "#\\" "-back-slant27-que-")
                      (str/replace "#lang racket" "")
                      (str/replace "#\\/" "-back-slant28-que-")
                      (str/replace "#:" "-back-slant29-que-")
                      (str/replace "::" "back-slant30-que")
                      (str/replace "#f" "sharp-function")
                      (str/replace "=>" "back-slant29-que")
                      (str/replace "#t" "sharp-tttttt")
                      (str/replace "#\\_" "back-slant30-que")
                      (str/replace "\\'" "back-slant31-que")
                      (str/replace "^" "-back-slant32-que")
                      (str/replace "#\\." "-back-slant33-que")
                      
                      (str/replace "#\\\"" #_"#\\\"" "-back-slant34-que")
                      (str/replace "#\\'" "-back-slant35-que")
                      (str/replace "#\\<" "-back-slant36-que")
                      (str/replace "#\\>" "-back-slant37-que")
                      ;;(str/replace "#\\\." "-back-slant33-que")
                      ;;(str/replace "#\\/" "-back-slant33-que-")
                      ))
                list-init (fn [st] (str "( " st " )"))
                code-list (->>
                           (slurp file-name)
                           remove-invalid-token
                           list-init
                           read-string)]
            (op-fn code-list file-name)))]
    (for [file-name file-names]
      (split-code file-name))))

#_(def scheme-ast (-> "lib/scheme-jimw-code/ydiff/htmlize.rkt" slurp

                    (fn [st] (str "'(" st ")"))
                    read-string))

;;(read-string (str "( " (slurp "lib/scheme-jimw-code/ydiff/htmlize.rkt") " )"))

(defn is-rkt-def
  [li]
  li)

;; (import-scheme-def-and-other scheme-ast "file-name" #(print %) #(print %))
(defn import-scheme-def-and-other
  [scheme-ast file-name save-def-fn save-other-fn]
  (let [res (clojure.walk/postwalk
             #(if (coll? %)
                (do
                  (if (is-rkt-def %)
                    (do
                      (save-def-fn (pp/write % :dispatch pp/code-dispatch :stream nil))
                      (list :def-scheme-function (second %)))
                    %))  %) scheme-ast)]
    (save-other-fn
     (pp/write res :dispatch pp/code-dispatch :stream nil))))
