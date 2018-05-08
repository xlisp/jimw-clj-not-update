(ns jimw-clj.db.crf
  (:require
   [clojure.string :as str]
   [clojure.java.shell :as shell]
   [clojure.pprint :as pp]
   [taoensso.timbre :refer [error debug info]]))

;; TODO: 代码的词性标注
;; 1. 第一个元素就是函数方法
;; 2. 其他就是参数
(def crf-code
  "(defn add-search-event-for-blog
  [{:keys [db blog eid]}]
  (jc1 db
       (-> (h/update :blogs)
           (h/sset
            {:search_events
             (sql/call :array_cat :search_events
                       (honeysql.types/array [eid]))})
           (h/where [:= :id blog]))))")

(comment

  (clojure.walk/postwalk
   #(cond
      (list? %) (first %)    
      :else %)
   (read-string crf-code)
   ) ;;=> defn

  ;; TODO: 将树形的代码打成向量
  (clojure.walk/postwalk
   #(if (coll? %)
      (prn %)
      %)
   (read-string crf-code)
   )
  ;; =========>>>
  ;; [db blog eid]
  ;; [:keys nil]
  ;; {}
  ;; [nil]
  ;; (h/update :blogs)
  ;; [eid]
  ;; (honeysql.types/array nil)
  ;; (sql/call :array_cat :search_events nil)
  ;; [:search_events nil]
  ;; {}
  ;; (h/sset nil)
  ;; [:= :id blog]
  ;; (h/where nil)
  ;; (-> nil nil nil)
  ;; (jc1 db nil)
  ;; (defn add-search-event-for-blog nil nil)
  ;; 

  ;; TODO: 提取出所有函数名,给这些函数加上函数的词性的标注
  (clojure.walk/postwalk
   #(if (coll? %)
      ;;(prn (first %))
      (if (list? %)
        (prn (first %))      
        ;;"not_function"
        nil)
      ;;
      %)
   (read-string crf-code)
   )
  ;; ===>>> OK: 提取所有的函数名
  ;; h/update
  ;; honeysql.types/array
  ;; sql/call
  ;; h/sset
  ;; h/where
  ;; ->
  ;; jc1
  ;; defn
  ;; 

  ;; TODO2: SQL字段的词性标注, Elisp或者instaparse写个SQL解析, 提取字段是什么类型的  
  ;;;; (get-sql-table-hash nil nil) ;;=> ("id" "UUID" "personal" "UUID" "asset" "UUID" "order_in_set" "INT" "metadata" "JSONB" ...))
  ;;(defun get-sql-table-hash (buffername op-lambda)
  ;;  (let* ((stri (get-mark-content
  ;;                (if (null buffername)
  ;;                  (current-buffer)
  ;;                  buffername)))
  ;;         (hash (setq myHash (make-hash-table :test 'equal)))
  ;;         (table-name
  ;;          (replace-regexp-in-string
  ;;           "_" "-"
  ;;           (car (last (butlast (split-string (first (split-string stri "\n")) "\s+"))))))
  ;;         (col-list-text (butlast (rest (split-string stri "\n"))))
  ;;         (get-col-name (lambda (col-st)
  ;;                               (let* ((split-str (rest (split-string col-st "\s+")))
  ;;                                      (id-name (first split-str))
  ;;                                      (type-name (first (rest split-str))))
  ;;                                 (if (or (null id-name) (string-equal "UNIQUE" id-name)) nil
  ;;                                     (puthash id-name type-name myHash)))))
  ;;         (returns (butlast (cons table-name (-map get-col-name col-list-text)))))
  ;;    (if (null op-lambda)
  ;;      (list table-name hash)
  ;;      (apply op-lambda (list table-name hash)))
  ;;    ))
  
  (def aaa (atom "")) ;;=> #atom[[:keys :aaaa] 0x293b7aef]
  ;; TODO: 将树形的代码打成向量, nil部分为尾实体
  (clojure.walk/postwalk
   #(if (coll? %)
      (do
        (prn %)
        ;;(prn "=======")
        (if (map? (type %))
          nil
          (do
            (reset! aaa %)
            :aaaa)
          )
        )
      %)
   (read-string crf-code)
   )
  ;; => 
  ;; [db blog eid]
  ;; [:keys :aaaa]
  ;; IllegalArgumentException Don't know how to create ISeq from: clojure.lang.Keyword  clojure.lang.RT.seqFrom (RT.java:542)
  
  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  ;; TODO: 二叉树算法的多维向量
  (clojure.walk/postwalk
   #(if (coll? %)
      (do
        (prn %)
        ;;(prn "=======")
        (if (map? (type %))
          nil
          ""
          )
        )
      %)
   (read-string crf-code)
   )
  ;; =>
  ;; [db blog eid]
  ;; [:keys ""]
  ;; {}
  ;; [""]
  ;; (h/update :blogs)
  ;; [eid]
  ;; (honeysql.types/array "")
  ;; (sql/call :array_cat :search_events "")
  ;; [:search_events ""]
  ;; {}
  ;; (h/sset "")
  ;; [:= :id blog]
  ;; (h/where "")
  ;; (-> "" "" "")
  ;; (jc1 db "")
  ;; (defn add-search-event-for-blog "" "")
  
  )
