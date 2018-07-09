(ns jimw-clj.db.java
  (:require
   [clojure.string :as str]
   [clojure.java.shell :as shell]
   [clojure.pprint :as pp]
   [taoensso.timbre :refer [error debug info]]
   [instaparse.core :as insta]))

;; db.core: (java/import-project-file-to-blog conn create-blog "testpro")
(defn import-project-file-to-blog
  [db create-blog project stype]
  (let [file-names
        (->
         (shell/sh "find"
                   (str "lib/java-jimw-code/" project)
                   "-name" "*.java")
         :out
         (clojure.string/split #"\n"))
        content-fn (fn [file-name] (str "```java\n" (slurp file-name) "\n```"))]
    (for [file-name file-names]
      (create-blog {:db db :name file-name :content (content-fn file-name)
                    :source_type stype}))))

#_(import-project-methods-to-blog
   {:db "test" :create-blog prn :project "clojure" :stype "SEMANTIC_SEARCH" :ltype "JAVA"})

(defn import-project-methods-to-blog
  [{:keys [db create-blog project stype ltype]}]
  (let [file-names
        (->
         (shell/sh "find"
                   (str "lib/java-jimw-code/" project)
                   "-name" "*.java.ast")
         :out
         (clojure.string/split #"\n"))
        content-fn (fn [content] (str "```java\n" content "\n```"))]
    (doseq [file-name file-names]
      (let [fcontent (slurp file-name)]
        (if (empty? fcontent)
          (prn (str file-name "----------空文件!"))
          (let [javas (clojure.string/split (slurp file-name)
                                            #"-----------split-line-----------------" )]
            (doseq [method javas]
              (do
                (prn (str file-name "-----------------"))
                (create-blog {:db db :name file-name
                              :project project
                              :content (content-fn method)
                              :source_type stype
                              :language_type ltype})
                )
              )
            )
          )
        )
      )
    )
  )
