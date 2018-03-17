(ns jimw-clj.db.java
  (:require
   [clojure.string :as str]
   [clojure.java.shell :as shell]
   [clojure.pprint :as pp]
   [taoensso.timbre :refer [error debug info]]
   [instaparse.core :as insta]))

;; db.core: (java/import-project-file-to-blog conn create-blog "testpro")
(defn import-project-file-to-blog
  [db create-blog project]
  (let [file-names
        (->
         (shell/sh "find"
                   (str "lib/java-jimw-code/" project)
                   "-name" "*.java")
         :out
         (clojure.string/split #"\n"))
        content-fn (fn [file-name] (str "```java\n" (slurp file-name) "\n```"))]
    (for [file-name file-names]
      (create-blog {:db db :name file-name :content (content-fn file-name)}))))
