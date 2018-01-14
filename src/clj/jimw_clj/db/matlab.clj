(ns jimw-clj.db.matlab
  (:require
   [clojure.string :as str]
   [clojure.java.shell :as shell]
   [clojure.pprint :as pp]
   [taoensso.timbre :refer [error debug info]]
   [instaparse.core :as insta]))

;; db.core: (matlab/import-project-file-to-blog conn create-blog)
(defn import-project-file-to-blog
  [db create-blog]
  (let [file-names
        (->
         (shell/sh "find" "lib" "-name" "*.m")
         :out
         (clojure.string/split #"\n"))
        content-fn (fn [file-name] (str "```matlab\n" (slurp file-name) "\n```"))]
    (for [file-name file-names]
      (create-blog {:db db :name file-name :content (content-fn file-name)}))))
