(ns jimw-clj.db.matlab
  (:require
   [clojure.string :as str]
   [clojure.java.shell :as shell]
   [clojure.pprint :as pp]
   [taoensso.timbre :refer [error debug info]]
   [instaparse.core :as insta]))

;; (bodol-parser "(map (λ a → (+ a 1)))")
;; => ([:LIST [:SYMBOL "map"] [:LAMBDA [:CLAUSE [:ARGS [:SYMBOL "a"]] [:BODY [:LIST [:SYMBOL "+"] [:SYMBOL "a"] [:NUMBER "1"]]]]]])
;; (bodol-parser " ") ;;  => Parse error
;; (bodol-parser "dasdas") ;; => ([:SYMBOL "dasdas"])
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

;; (js-parser "function onLine(line)")
;; (js-parser "function") ;; => [:DEFUN_KEYWORD "function"]
;; (js-parser "sdadsa")
;; (js-parser "function aaa(bbb){return(123)}")
;; (def js-parser
;;   (insta/parser
;;    "
;; DEFUN = #'^function \\s*\\(\\)\\s*\\{\\s*return\\s*([\\s\\S]*);\\s*\\}'
;; "))
;; 
;; (js-parser "function onLine(line) {line}")
;;DEFUN = <DEFUN_KEYWORD> SPACE SYMBOL <'('> SYMBOL <')'> SPACE <'{'> SYMBOL <'}'>

;;(str "AAAAAA" (slurp "bbb") "BBBBBB")

;; (test-parser "#| dasdsadsa \n dsadsa \n dasdas |#")
;; => [:comment "#| dasdsadsa \n dsadsa \n dasdas |#"]
(def test-parser
  (insta/parser "comment = #'(?s)[#\\|]+(.*?)\\|#'"))
