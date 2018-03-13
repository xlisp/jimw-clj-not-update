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
         #_(remove #(or
                   (= % "lib/jimw-code/clojure/test/clojure/test_clojure/reader.cljc")
                   (= % "lib/jimw-code/clojure/test/clojure/test_clojure/java_interop.clj"))))
        split-code
        (fn [file-name]
          (let [_ (prn (str file-name " >>>>>>"))
                remove-invalid-token
                (fn [st]
                  (-> st
                      (str/replace #"::" "double-colon-")
                      (str/replace #"#js" "" #_"the-tag-js")
                      (str/replace #"#\?" "")
                      (str/replace #"#\"" "\"")
                      (str/replace #"\\\." "-back-slant1-dot-")
                      (str/replace #"\\\\" "-back-slant2-")
                      (str/replace #"\\d" "-back-slant3-num-")
                      (str/replace #"\\-" "-back-slant4--")
                      (str/replace #"\\\?" "-back-slant5-que-")
                      (str/replace #"\\\*" "-back-slant6-que-")
                      (str/replace #"\\\/" "-back-slant7-que-")
                      (str/replace #"\\\)" "-back-slant8-que-")
                      (str/replace #"\\\#" "-back-slant9-que-")
                      (str/replace #"\\s" "-back-slant10-que-")
                      (str/replace "\\{" "-back-slant11-que-")
                      (str/replace "\\}" "-back-slant12-que-")
                      (str/replace "\\W" "-back-slant13-que-")
                      (str/replace "\\w" "-back-slant14-que-")
                      (str/replace "\\S" "-back-slant15-que-")
                      (str/replace "\\$" "-back-slant16-que-")
                      (str/replace "\\(" "-back-slant17-que-")
                      (str/replace "\\Q" "-back-slant18-que-")
                      (str/replace "\\E" "-back-slant19-que-")
                      (str/replace "\\[" "-back-slant20-que-")
                      (str/replace "\\]" "-back-slant21-que-")
                      (str/replace "\\>" "-back-slant22-que-")
                      (str/replace "\\:" "-back-slant23-que-")
                      (str/replace "\\<" "-back-slant24-que-")
                      (str/replace "\\!" "-back-slant25-que-")
                      (str/replace "\\+" "-back-slant26-que-")
                      (str/replace "#="  "")))
                list-init (fn [st] (str "( " st " )"))
                code-list (->>
                           (slurp file-name)
                           remove-invalid-token
                           list-init
                           read-string)]
            (op-fn code-list file-name)))]
    (for [file-name file-names]
      (split-code file-name))))
