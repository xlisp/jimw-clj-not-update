(ns jimw-clj.db.scheme
  (:require
   [clojure.string :as str]
   [clojure.java.shell :as shell]
   [clojure.pprint :as pp]
   [taoensso.timbre :refer [error debug info]]))

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
                      (str/replace "#\\\"" "-back-slant34-que")
                      (str/replace "#\\'" "-back-slant35-que")
                      (str/replace "#\\<" "-back-slant36-que")
                      (str/replace "#\\>" "-back-slant37-que")))
                list-init (fn [st] (str "( " st " )"))
                code-list (->>
                           (slurp file-name)
                           remove-invalid-token
                           list-init
                           read-string)]
            (op-fn code-list file-name)))]
    (for [file-name file-names]
      (split-code file-name))))

;; (read-string-for-file (fn [code-list file-name] (map first code-list)) "lib/scheme-jimw-code/ydiff/parse-lisp.rkt")
(defn read-string-for-file
  [op-fn file-name]
  (let [split-code
        (fn [file-name]
          (let [_ (prn (str file-name " >>>>>>"))
                remove-invalid-token
                (fn [st]
                  (-> st
                      (str/replace "#lang racket" "")
                      (str/replace "#f" "sharp-function")
                      (str/replace "#\\'" "back-slant31-que")
                      (str/replace "#\\/" "-back-slant28-que-")
                      (str/replace "#:" "-back-slant29-que-")
                      (str/replace "::" "back-slant30-que")
                      (str/replace "=>" "back-slant29-que")
                      (str/replace "#t" "sharp-tttttt")
                      (str/replace "#\\_" "back-slant30-que")
                      (str/replace "^" "-back-slant32-que")
                      (str/replace "#\\." "-back-slant33-que")
                      (str/replace "#\\\"" "-back-slant34-que")
                      (str/replace "#\\'" "-back-slant35-que")
                      (str/replace "#\\<" "-back-slant36-que")
                      (str/replace "#\\>" "-back-slant37-que")
                      (str/replace "#\\+" "back-slant38-que")
                      ))
                list-init (fn [st] (str "( " st " )"))
                code-list (->>
                           (slurp file-name)
                           remove-invalid-token
                           list-init
                           read-string)]
            (op-fn code-list file-name)))]
    (split-code file-name)))

;; (slurp "lib/scheme-jimw-code/ydiff/aaa")
;; (def scheme-ast-eg (first (read-string-for-pro (fn [code-list file-name] code-list) "ydiff")))

(defn is-rkt-define
  [li]
  (= (first li) (symbol "define")))

;; TODOS: 把postwalk做成一个结构搜索: 搜索define结构, **复合算法结构
;; (search-scheme-def-and-other scheme-ast-eg "file-name" #(print %) #(print (str "======" %)))
(defn search-scheme-def-and-other
  [scheme-ast file-name save-def-fn save-other-fn]
  (let [res (clojure.walk/postwalk
             #(if (coll? %)
                (do
                  (if (is-rkt-define %) #_(is-rkt-def %)
                      (do
                        (save-def-fn (pp/write % :dispatch pp/code-dispatch :stream nil))
                        (list :def-scheme-function (second %)))
                      %))  %) scheme-ast)]
    (save-other-fn
     (pp/write res :dispatch pp/code-dispatch :stream nil))))
