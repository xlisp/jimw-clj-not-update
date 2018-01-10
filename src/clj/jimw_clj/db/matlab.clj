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

;; => (test2-parser "{dsadas}") ;;=> [:comment "{dsadas}"]
(def test2-parser
  (insta/parser "comment = #'(?s)[{]+(.*?)}'"))

;;(re-find #"function[ ]+[\w]+" "function aaa") ;; => "function aaa"
;;(re-find #"function[ ]+[\w]+[ ]?\([\w]+\)[ ]?" "function aaa(bbb)") ;; => "function aaa(bbb)"
(def test3-parser
  (insta/parser "funhead = #'function[ ]+[\\w]+'
funbody = #'(?s)[{]+(.*?)}'
"))
;; (test3-parser "function aaa") ;;=> [:funhead "function aaa"]

#_(re-find #"function(?s)[\(]+(.*?)\)(?s)[{]+(.*?)}"
         "function(dasdas\n dasdas){dsadas\n dasds32321 }")
;; => ["function(dasdas\n dasdas){dsadas\n dasds32321 }" "dasdas\n dasdas" "dsadas\n dasds32321 "]

#_(re-find #"function[ ]+[\w]+[ ]?(?s)[\(]+(.*?)\)[ ]?(?s)[{]+(.*?)}"
         "function aaa (dasdas){dsadas\n dasds32321 }")
;;=> ["function aaa (dasdas){dsadas\n dasds32321 }" "dasdas" "dsadas\n dasds32321 "]

;; 所以必须所有的{}括号和()都要被识别是什么类型的表达式才行, 否则只会匹配到了一只就放弃匹配了, 而是要递归的匹配结构
#_(re-find #"function[ ]+[\w]+[ ]?(?s)[\(]+(.*?)\)[ ]?(?s)[{]+(.*?)}"
         "function aaa (dasdas){dsadas\n dasds32321{dsadsadsa} 32132321 \n  }")
;; => ["function aaa (dasdas){dsadas\n dasds32321{dsadsadsa}" "dasdas" "dsadas\n dasds32321{dsadsadsa"]


;; (test5-parser "functiondsadsa") ;;=> [:DEFUN [:DEFUN_KEYWORD "function"] [:SYMBOL "dsadsa"]]
(def test5-parser
  (insta/parser "
DEFUN = DEFUN_KEYWORD SYMBOL
SYMBOL = #'[\\w]+'
DEFUN_KEYWORD = 'function'
"))

;; (test6-parser "function dsadsa") ;;=> [:DEFUN [:DEFUN_KEYWORD "function"] [:SYMBOL "dsadsa"]]
(def test6-parser
  (insta/parser "
DEFUN = DEFUN_KEYWORD space+ SYMBOL
SYMBOL = #'[\\w]+'
DEFUN_KEYWORD = 'function'
<space> = <#'[ ]+'>
"))

;; (test7-parser "function dsadsa(dasdsa){dasdsadsa\ndsads}")
;; => [:DEFUN [:DEFUN_KEYWORD "function"] [:SYMBOL "dsadsa"] [:ARGS "(dasdsa)"] [:BODY "{dasdsadsa\ndsads}"]]
(def test7-parser
  (insta/parser "
DEFUN = DEFUN_KEYWORD space+ SYMBOL ARGS BODY
SYMBOL = #'[\\w]+'
DEFUN_KEYWORD = 'function'
<space> = <#'[ ]+'>
ARGS = #'(?s)[\\(]+(.*?)\\)'
BODY = #'(?s)[{]+(.*?)}'
"))

;; (test7-parser "function dsadsa(dasdsa){dasdsadsa\ndsads (dasdas) \n dasds321321}")
;;  => [:DEFUN [:DEFUN_KEYWORD "function"] [:SYMBOL "dsadsa"] [:ARGS "(dasdsa)"] [:BODY "{dasdsadsa\ndsads (dasdas) \n dasds321321}"]]

;; (test7-parser "function dsadsa(dasdsa){dasdsadsa\ndsads (dasdas) \n dasds321321 {dasdas \n dasdsa321312} }") ;;=> 解析失败

(def test8-parser
  (insta/parser "
<SEXP> = (LIST / SYMBOL)
LIST = <'('> SEXP <')'>
SYMBOL = #'[\\w]+'
"))

;; (test8-parser "(dasdas)") ;;=> ([:LIST [:SYMBOL "dasdas"]])
;; (test8-parser "(dasdas dasdsa)") ;; 错误
;; (test8-parser "((dasdas))") ;;=> ([:LIST [:LIST [:SYMBOL "dasdas"]]])

(def test9-parser
  (insta/parser "
<SEXP> = (DEFUN / SYMBOL / ARGS / BODY)
DEFUN = DEFUN_KEYWORD space+ SYMBOL ARGS BODY
SYMBOL = #'[\\w]+'
DEFUN_KEYWORD = 'function'
<space> = <#'[ ]+'>
ARGS = <'('> SEXP <')'>
BODY = <'{'> SEXP <'}'>
"))

;; (test9-parser "function dsadsa(dasdsa){({uuiijj})}")
;; => ([:DEFUN [:DEFUN_KEYWORD "function"] [:SYMBOL "dsadsa"] [:ARGS [:SYMBOL "dasdsa"]] [:BODY [:ARGS [:BODY [:SYMBOL "uuiijj"]]]]])
