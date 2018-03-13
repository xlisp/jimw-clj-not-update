(ns jimw-clj.db.emacs
  (:require
   [clojure.string :as str]
   [clojure.java.shell :as shell]
   [clojure.pprint :as pp]
   [taoensso.timbre :refer [error debug info]]
   [instaparse.core :as insta]))

;; (read-string (str "(" (slurp "lib/emacs-jimw-code/cider/cider-client.el") ")"))
;; (read-string-for-pro (fn [code-list file-name] (map first code-list)) "cider")
(defn read-string-for-pro
  [op-fn & project]
  (let [file-names
        (->>
         (->
          (shell/sh "find"
                    (if project
                      (str "lib/emacs-jimw-code/" (first project))
                      "lib") "-name" "*.el") :out
          (clojure.string/split #"\n"))
         (remove #(or
                   (= % "lib/emacs-jimw-code/cider/cider-mode.el")
                   (= % "lib/emacs-jimw-code/cider/test/cider-tests--no-auto.el"))))
        split-code
        (fn [file-name]
          (let [_ (prn (str file-name " >>>>>>"))
                remove-invalid-token
                (fn [st]
                  (-> st
                      #_(str/replace #"::" "double-colon-")
                      #_(str/replace #"#js" "" #_"the-tag-js")
                      #_(str/replace #"#\?" "")
                      #_(str/replace #"#\"" "\"")
                      #_(str/replace #"\\\." "-back-slant1-dot-")
                      #_(str/replace #"\\\\" "-back-slant2-")
                      #_(str/replace #"\\d" "-back-slant3-num-")
                      #_(str/replace #"\\\-" "-back-slant4--")
                      #_(str/replace (re-pattern "\\-") "-back-slant4--")
                      #_(str/replace #"\\\?" "-back-slant5-que-")
                      #_(str/replace #"\\\*" "-back-slant6-que-")
                      #_(str/replace #"\\\/" "-back-slant7-que-")
                      #_(str/replace #"\\\)" "-back-slant8-que-")
                      #_(str/replace #"\\\#" "-back-slant9-que-")
                      #_(str/replace #"\\s" "-back-slant10-que-")
                      #_(str/replace "\\{" "-back-slant11-que-")
                      #_(str/replace "\\}" "-back-slant12-que-")
                      #_(str/replace "\\W" "-back-slant13-que-")
                      #_(str/replace "\\w" "-back-slant14-que-")
                      #_(str/replace "\\S" "-back-slant15-que-")
                      #_(str/replace "\\$" "-back-slant16-que-")
                      #_(str/replace "\\#_(" "-back-slant17-que-")
                      #_(str/replace "\\Q" "-back-slant18-que-")
                      #_(str/replace "\\E" "-back-slant19-que-")
                      #_(str/replace "\\[" "-back-slant20-que-")
                      #_(str/replace "\\]" "-back-slant21-que-")
                      #_(str/replace "\\>" "-back-slant22-que-")
                      #_(str/replace "\\:" "-back-slant23-que-")
                      #_(str/replace "\\<" "-back-slant24-que-")
                      #_(str/replace "\\!" "-back-slant25-que-")
                      #_(str/replace "\\+" "-back-slant26-que-")
                      #_(str/replace "#="  "")
                      (str/replace "1-"  "one-jian")
                      (str/replace "1+"  "one-jia")
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

;; 如何项目一出来,就自动把read-string的敏感字符给替换掉呢?
;; 1.replace的效率太低下了,纯体力活
;; 2. 手工vim去掉它们,效率也不高,而且不支持更新 ==>> 效率高些,因为避免了1.replace带来的副作用(副作用带来更不明显的特征了)

;;;推荐方法=>>> 先用vim修改,统计结果,然后再改成read-string

;; 比如: lib/emacs-jimw-code/cider/cider-mode.el
;; RuntimeException Unmatched delimiter: )  clojure.lang.Util.runtimeException
;; 很难去掉
;; =====+>>> 通用的方法就是写一个r-scm一样的方法: 一个表达式一个表达式遍历
;; 2. 方法二: 加入错误字符号来二分判断错误: `(6- aaa)`

;; 3. 只要vim一保存就调用` (read-string-for-pro (fn [code-list file-name] (map first code-list)) "cider")` 


;; lib/emacs-jimw-code/cider/cider-mode.el ;=>
;; RuntimeException EOF while reading
;; 结尾不能`; or ;;` 

