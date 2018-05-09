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

;; TODO: 可以表达增量编辑的差别
(def crf-code-1
  "(defn add-search-event-for-blog
  [{:keys [db blog eid name]}]
  (jc1 db
       (-> (h/update :blogs)
           (h/sset
            {:search_events
             (sql/call :array_cat :search_events
                       (honeysql.types/array [eid]))})
           (h/merge-where [:= :name name])
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
  (def crf-code-atom (atom []))
  ;; TODO: 二叉树算法的多维向量
  (clojure.walk/postwalk
   #(if (coll? %)
      (do
        (prn %)
        (swap! crf-code-atom conj %)
        "")
      
      %)
   (read-string crf-code)
   )
  ;;=> A. 从最下往上数, 除了{}, 没有了""是个最小点
  ;;   B. 如果只是含有一个"",默认就是,上一个的id
  ;;   C. 如果含有两个和以上"", 就会从第一组最后一个id开始数

  ;; =====>> 向量分类: 参数组
  ;; 1 [db blog eid]      => crf-code-1: [db blog eid name]
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

  ;; ===================>> crf-code-1:
  ;; [:= :name name]
  ;; (h/merge-where "")
  
  ;; ======>>> 向量分类: where组
  ;; 12 [:= :id blog]
  ;; 13 (h/where "") ;;=> (h/where "查询字段12")
  
  ;; ======>>> 向量分类: defn组
  ;; 14 (-> "" "" "") ;;=> (-> "Update5" "Sset11" "Where13") ;;=> crf-code-1:(-> "" "" "" "")
  ;; 15 (jc1 db "") ;;=> (jc1 db "SQL14")
  ;; 16 (defn add-search-event-for-blog "" "") ;;=> (defn add-search-event-for-blog "参数4" "Body15")

  ;;;;;;;;;;;;;;;;;;;;;;;;;
  (def crf-code-1-atom (atom []))
  ;; TODO: 两段代码的相似度计算,也就是字符串向量相等匹配的比例有多少
  (clojure.walk/postwalk
   #(if (coll? %)
      (do
        (prn %)
        (swap! crf-code-1-atom conj %)
        "")
      
      %)
   (read-string crf-code-1)
   )
  ;;
  ;; 三个元素: 
  (clojure.data/diff [1 2 3] [5 9 3 2 3 7])
  ;;=> [[1 2] [5 9 nil 2 3 7] [nil nil 3]]

  ;; 前者是后者的子集时: 第三个元素为nil
  (clojure.data/diff [2 3] [5 9 3 2 3 7])
  ;;=> [[2 3] [5 9 3 2 3 7] nil]

  ;; 后者是前者的子集时: 第三个元素为nil
  (clojure.data/diff [5 9 3 2 3 7] [2 3])
  ;;=> [[5 9 3 2 3 7] [2 3] nil]

  ;; @@@@ 后者的中间2,改成是8
  (clojure.data/diff [5 9 3 2 3 7] [5 9 3 8 3 7])
  ;;=> [[nil nil nil 2] [nil nil nil 8] [5 9 3 nil 3 7]]
  ;;   第一个元素的不同, 第二个元素的不同, 两个元素相同的地方

  (prn (clojure.data/diff @crf-code-atom  @crf-code-1-atom))
  ;; =>
  [
   ;; @crf-code-atom的不同
   [nil nil nil nil nil nil nil nil nil nil nil [nil :id blog] [h/where] [-> "" "" ""] [jc1 db ""] [defn add-search-event-for-blog]]
   ;; @crf-code-1-atom的不同
   [[nil nil nil name] nil nil nil nil nil nil nil nil nil nil [nil :name name] [h/merge-where] [:= :id blog] [h/where ""] [-> "" nil nil ""] (jc1 db "") (defn add-search-event-for-blog "" "")]
   ;; 两个元素相同的地方
   [[db blog eid] [:keys ""] {} [""] (h/update :blogs) [eid] (honeysql.types/array "") (sql/call :array_cat :search_events "") [:search_events ""] {} (h/sset "") [:=] [nil ""] nil nil [nil nil "" ""]]
   ]

  ;; =>
  (prn (clojure.data/diff (map str @crf-code-atom)  (map str @crf-code-1-atom)))
  ;;=>
  [
   ["[db blog eid]" nil nil nil nil nil nil nil nil nil nil "[:= :id blog]" "(h/where \"\")" "(-> \"\" \"\" \"\")" "(jc1 db \"\")" "(defn add-search-event-for-blog \"\" \"\")"]
   ["[db blog eid name]" nil nil nil nil nil nil nil nil nil nil "[:= :name name]" "(h/merge-where \"\")" "[:= :id blog]" "(h/where \"\")" "(-> \"\" \"\" \"\" \"\")" "(jc1 db \"\")" "(defn add-search-event-for-blog \"\" \"\")"]
   [nil "[:keys \"\"]" "{}" "[\"\"]" "(h/update :blogs)" "[eid]" "(honeysql.types/array \"\")" "(sql/call :array_cat :search_events \"\")" "[:search_events \"\"]" "{}" "(h/sset \"\")"]
   ]  
  ;;

  ;; 不考虑顺序的情况下,考虑集合的相似度
  (prn (clojure.set/intersection
        (set (map str @crf-code-atom))
        (set (map str @crf-code-1-atom))))
  ;;=>
  #{"(honeysql.types/array \"\")" "(jc1 db \"\")" "(h/where \"\")" "(h/sset \"\")" "(defn add-search-event-for-blog \"\" \"\")" "[:= :id blog]" "[:search_events \"\"]" "[\"\"]" "(sql/call :array_cat :search_events \"\")" "[:keys \"\"]" "[eid]" "(h/update :blogs)" "{}"}

  (prn (clojure.set/difference
        (set (map str @crf-code-atom))
        (set (map str @crf-code-1-atom))))
  ;;=> #{"[db blog eid]" "(-> \"\" \"\" \"\")"}

  
  (for [a '(1 2 3 4 5) b '(3 2 7 8 10)
        :when
        (= a b)]
    a) ;; => (2 3)
  
  (for [a (map str @crf-code-atom) b (map str @crf-code-1-atom)
        :when
        (= a b)]
    a)

  ;; https://stackoverflow.com/questions/23199295/how-to-diff-substract-two-lists-in-clojure
  (defn diff [s1 s2]
    (mapcat
     (fn [[x n]] (repeat n x))
     (apply merge-with - (map frequencies [s1 s2]))))
  (def L1  [1 1 1 3 3 4 4 5 5 6])
  (def L2  [1     3 3   4 5 ])
  (diff L1 L2) ;;=> (1 1 4 5 6)

  ;; 111111111111: 最小不同的地方在哪里OK
  (diff (map str @crf-code-atom)  (map str @crf-code-1-atom))
  ;; => ("[db blog eid]" "[db blog eid name]" "(-> \"\" \"\" \"\")" "[:= :name name]" "(-> \"\" \"\" \"\" \"\")" "(h/merge-where \"\")")


  ;; 只能比较数组
  (defn sdiff 
    [[x & rx :as xs] [y & ry :as ys]]
    (lazy-seq 
     (cond
       (empty? xs) nil
       (empty? ys) xs
       :else (case (compare x y)
               -1 (cons x (sdiff rx ys))
               0 (sdiff rx ry)
               +1 (sdiff xs ry)))))
  (def LL1 [1 1 1 3 3 4 4 5 5 6])
  (def LL2 [1 3 3 4 5])
  (sdiff LL1 LL2) ;; => (1 1 4 5 6)

  ;;(sdiff (map str @crf-code-atom)  (map str @crf-code-1-atom))
  ;; IllegalArgumentException No matching clause: 61  jimw-clj.db.crf/sdiff/fn--74887 (form-init4708390340965895619.clj:275)
  
  
)
