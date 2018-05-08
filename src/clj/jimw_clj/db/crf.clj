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
        "")
      
      %)
   (read-string crf-code)
   )
  ;;=> A. 从最下往上数, 除了{}, 没有了""是个最小点
  ;;   B. 如果只是含有一个"",默认就是,上一个的id
  ;;   C. 如果含有两个和以上"", 就会从第一组最后一个id开始数

  ;; =====>> 向量分类: 参数组
  ;; 1 [db blog eid]
  ;; 2 [:keys ""]
  ;; 3 {}
  ;; 4 [""] ;;=> ["函数参数3"]

  ;; =====>> 向量分类: update组
  ;; 5 (h/update :blogs)

  ;; =====>>> 向量分类: sset组
  ;; 6 [eid]
  ;; 7 (honeysql.types/array "")
  ;; 8 (sql/call :array_cat :search_events "")
  ;; 9 [:search_events ""]
  ;; 10 {}
  ;; 11 (h/sset "") ;;=> (h/sset "更新字段10")

  ;; ======>>> 向量分类: where组
  ;; 12 [:= :id blog]
  ;; 13 (h/where "") ;;=> (h/where "查询字段12")
  
  ;; ======>>> 向量分类: defn组
  ;; 14 (-> "" "" "") ;;=> (-> "Update5" "Sset11" "Where13")
  ;; 15 (jc1 db "") ;;=> (jc1 db "SQL14")
  ;; 16 (defn add-search-event-for-blog "" "") ;;=> (defn add-search-event-for-blog "参数4" "Body15")

)
