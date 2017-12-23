(ns jimw-clj.db.ruby
  (:require
   [clojure.string :as str]
   [clojure.java.shell :as shell]
   [taoensso.timbre :refer [error debug info]]))

;; (read-string-for-pro (fn [code-list file-name] (map first code-list)) "rails")
(defn read-string-for-pro
  [op-fn & project]
  (let [file-names
        (->>
         (->
          (shell/sh "find"
                    (if project
                      (str "lib/ruby-jimw-code/" (first project))
                      "lib") "-name" "*.ast") :out
          (clojure.string/split #"\n"))
         (remove #(or
                   (= % "lib/ruby-jimw-code/rails/actionpack/lib/action_controller/metal/head.rb.ast")
                   (= % "lib/ruby-jimw-code/rails/actionpack/lib/action_controller/metal/parameter_encoding.rb.ast")
                   (= % "lib/ruby-jimw-code/rails/actionpack/lib/action_controller/metal/live.rb.ast")
                   (= % "lib/ruby-jimw-code/rails/actionpack/lib/action_controller/metal/instrumentation.rb.ast")
                   (= % "lib/ruby-jimw-code/rails/actionpack/test/dispatch/request/query_string_parsing_test.rb.ast")
                   (= % "lib/ruby-jimw-code/rails/actionpack/test/dispatch/executor_test.rb.ast")
                   (= % "lib/ruby-jimw-code/rails/actionpack/test/dispatch/test_response_test.rb.ast")
                   (= % "lib/ruby-jimw-code/rails/actionpack/test/dispatch/request_id_test.rb.ast"))))
        split-code
        (fn [file-name]
          (let [_ (prn (str file-name " >>>>>>"))
                remove-invalid-token
                (fn [st]
                  (-> st
                      (str/replace #":@" ":at-rb")
                      (str/replace #":\[\]" ":arra-rb")
                      (str/replace #":\"(.*)\"" ":rb-symst-$1")
                      (str/replace ":*" ":rbstar")
                      (str/replace ":<<" ":rbleftleft")
                      (str/replace ":%" ":rbbaifen")
                      ))
                ;; TODOS catch 不了read-string的错误,以及语法和参数错误
                code-list
                (try
                  (read-string (remove-invalid-token (slurp file-name)))
                  (catch Exception e ()))
                
                #_(->>
                 (slurp file-name)
                 remove-invalid-token
                 read-string)
                #_(try
                    (->>
                             (slurp file-name)
                             remove-invalid-token
                             read-string)
                            (catch Exception e
                              (info (str "-->>" file-name ",Error" e))
                              ())
                            (finally (info (str "-->>" file-name ",Error")) ()))]
            (op-fn code-list file-name)))]
    (for [file-name file-names]
      (split-code file-name))))

;; (read-string "(aaa :\".no_action\")")

;;(read-string "(aaa :aaa=)")
;;(str/replace "(aaa :\".no_action\")" #":\"(.*)\"" ":rb-symst-$1")

#_(try
  (read-string "(aa :[])")
  (catch Exception e
    ()))

;; (read-string (slurp "lib/ruby-jimw-code/rails/actionpack/lib/action_controller/metal/parameter_encoding.rb.ast"))

;; => (module (const nil :ActionController) ok呀
#_(read-string
 (->
  (slurp "lib/ruby-jimw-code/rails/actionpack/lib/action_controller/metal/parameter_encoding.rb.ast")
  (str/replace #":@" ":at-rb")
  (str/replace #":\[\]" ":arra-rb")
  )
 )


;; 也ok... ==>> 那只能说明已有的Clojure代码正则替换错误了
#_(read-string
 (->
  (slurp  "lib/ruby-jimw-code/rails/actionpack/lib/action_controller/metal/instrumentation.rb.ast")
  (str/replace #":@" ":at-rb")
  (str/replace #":\[\]" ":arra-rb")
  )
 )
