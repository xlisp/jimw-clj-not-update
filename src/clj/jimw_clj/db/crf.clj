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



  )
