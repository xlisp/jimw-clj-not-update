(ns jimw-clj.db.javascript
  (:require
   [clojure.string :as str]
   [clojure.java.shell :as shell]
   [clojure.pprint :as pp]
   [taoensso.timbre :refer [error debug info]]
   [instaparse.core :as insta]))

;; (js-parser "function hello(xyz){ alert(abcde) }") ;;=>
#_[:DEFUN
   [:ARGS [:DEFUN_KEYWORD "function"] [:SYMBOL "hello"] [:SYMBOL "xyz"]]
   [:BODY [:CALL [:SYMBOL "alert"] [:SYMBOL "abcde"]]]]
(def js-parser
  (insta/parser "
<SEXP> = (DEFUN / SYMBOL / ARGS / BODY / CALL / space+)
DEFUN = ARGS BODY
SYMBOL = #'[\\w]+'
DEFUN_KEYWORD = 'function'
<space> = <#'[ ]+'>
CALL = SYMBOL <'('> SEXP <')'>
ARGS = DEFUN_KEYWORD space+ SYMBOL <'('> SEXP <')'>
BODY = <'{'> space? SEXP space? <'}'>
"))
