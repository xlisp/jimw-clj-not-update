(ns jimw-clj.db.matlab
  (:require
   [clojure.string :as str]
   [clojure.java.shell :as shell]
   [clojure.pprint :as pp]
   [taoensso.timbre :refer [error debug info]]
   [instaparse.core :as insta]))

;; db.core: (matlab/import-project-file-to-blog conn create-blog)
(defn import-project-file-to-blog
  [db create-blog]
  (let [file-names
        (->
         (shell/sh "find" "lib" "-name" "*.m")
         :out
         (clojure.string/split #"\n"))
        content-fn (fn [file-name] (str "```matlab\n" (slurp file-name) "\n```"))]
    (for [file-name file-names]
      (create-blog {:db db :name file-name :content (content-fn file-name)}))))

;; 语法解析的例子
;; (-> (bodol-parser "(map (λ a → (+ a 1)))") pp/pprint)
#_([:LIST
    [:SYMBOL "map"]
    [:LAMBDA
     [:CLAUSE
      [:ARGS [:SYMBOL "a"]]
      [:BODY [:LIST [:SYMBOL "+"] [:SYMBOL "a"] [:NUMBER "1"]]]]]])
(def bodol-parser
  (insta/parser
   "
<PROGRAM> = SPACE* SEXPS SPACE*
<SEXPS> = (SEXP SPACE+)* SEXP
<SEXP> = (QUOTED / DEFUN / LAMBDA / DOTTED / LIST / VECTOR / SYMBOL / NUMBER / STRING / BOOLEAN)
QUOTED = <QUOTE> SEXP
QUOTE = <'\\''>
DOTTED = <'('> SPACE* SEXP SPACE <'.'> SPACE SEXP SPACE* <')'>
LIST = <'('> SPACE* !((DEFUN_KEYWORD | LAMBDA_KEYWORD) (SPACE+ | <')'>)) SEXPS* SPACE* <')'>
VECTOR = <'['> SPACE* SEXPS* SPACE* <']'>
DEFUN = <'('> SPACE* <DEFUN_KEYWORD> SPACE+ SYMBOL SPACE+ CLAUSES SPACE* <')'>
DEFUN_KEYWORD = 'ƒ' | 'defn' | 'defun'
LAMBDA = <'('> SPACE* <LAMBDA_KEYWORD> SPACE+ CLAUSES SPACE* <')'>
LAMBDA_KEYWORD = 'λ' | 'fn' | 'lambda'
<CLAUSES> = (CLAUSE SPACE+)* CLAUSE
CLAUSE = ARGS ARROW SPACE+ BODY
ARGS = (!(ARROW SPACE+) SEXP SPACE+)*
<ARROW> = <'->'> | <'→'>
BODY = SEXP
BOOLEAN = '#t' | '#f'
NUMBER = NEGATIVE* (FRACTION | DECIMAL | INTEGER)
<NEGATIVE> = '-'
<FRACTION> = INTEGER '/' INTEGER
<DECIMAL> = INTEGER '.' INTEGER
<INTEGER> = #'\\p{Digit}+'
STRING = '\\\"' #'([^\"\\\\]|\\\\.)*' '\\\"'
SYMBOL = #'[\\pL_$&/=+~:<>|§?*-][\\pL\\p{Digit}_$&/=+~.:<>|§?*-]*'
<SPACE> = <#'[ \t\n,]+'>
"))
