(defproject jimw-clj "0.1.0-SNAPSHOT"

  :description "FIXME: write description"
  :url "http://example.com/FIXME"

  :dependencies [[cider/cider-nrepl "0.15.0-SNAPSHOT"]
                 ;;[cljs-ajax "0.5.9"]
                 [compojure "1.6.0"]
                 ;;[conman "0.6.3"]
                 [cprop "0.1.10"]
                 ;;[funcool/struct "1.0.0"]
                 [luminus-immutant "0.2.3"]
                 [luminus-migrations "0.3.3"]
                 [luminus-nrepl "0.1.4"]
                 [luminus/ring-ttl-session "0.3.2"]
                 ;;[markdown-clj "0.9.99"]
                 [metosin/muuntaja "0.2.1"]
                 [metosin/ring-http-response "0.8.2"]
                 [mount "0.1.11"]
                 [functionalbytes/mount-lite "2.0.0"]
                 [org.clojure/clojure "1.8.0"]
                 [org.clojure/clojurescript "1.9.521" :scope "provided"]
                 [org.clojure/tools.cli "0.3.5"]
                 [org.clojure/tools.logging "0.3.1"]
                 [org.postgresql/postgresql "42.0.0"]
                 ;;[org.webjars.bower/tether "1.4.0"]
                 [org.webjars/bootstrap "4.0.0-alpha.5"]
                 [org.webjars/font-awesome "4.7.0"]
                 [org.webjars/webjars-locator-jboss-vfs "0.1.0"]
                 [reagent "0.6.1"]
                 [reagent-utils "0.2.1"]
                 [re-frame "0.10.2"]
                 [ring-webjars "0.1.1"]
                 [ring/ring-core "1.6.0"]
                 [ring/ring-defaults "0.3.0"]
                 [secretary "1.2.3"]
                 [selmer "1.10.7"]
                 [honeysql "0.9.1"]
                 [nilenso/honeysql-postgres "0.2.3"]
                 [cheshire "5.8.0"]
                 [ring/ring-json "0.4.0"]
                 [com.taoensso/timbre "4.10.0"]
                 [cljsjs/marked "0.3.5-0"]
                 [cljs-http "0.1.43"]
                 [cljsjs/highlight "9.12.0-0"]
                 [com.cemerick/url "0.1.1"]
                 [buddy/buddy-sign "2.2.0"]
                 [buddy/buddy-hashers "1.3.0"]
                 [alandipert/storage-atom "2.0.1"]
                 [cljsjs/jquery "3.2.1-0"]
                 ;;[clj-jri "0.1.1-SNAPSHOT"]
                 [hikari-cp "1.8.2"]
                 [com.taoensso/sente "1.12.0"]
                 [me.raynes/conch "0.8.0"]
                 [com.huaban/jieba-analysis "1.0.2"]
                 ;;[hanlping "0.1.1-SNAPSHOT"]
                 [instaparse "1.4.8"]
                 [pdfboxing "0.1.13"]
                 [com.github.kenglxn.qrgen/javase "2.4.0"]
                 ;; 用出去incanter
                 ;;[steve-incanter "1.9.3-SNAPSHOT"]
                 [steve-incanter/incanter-core "1.9.3-SNAPSHOT"]
                 [steve-incanter/incanter-io "1.9.3-SNAPSHOT" :exclusions [org.clojure/clojure]]
                 [steve-incanter/incanter-charts "1.9.3-SNAPSHOT" :exclusions [org.clojure/clojure]]
                 [steve-incanter/incanter-svg "1.9.3-SNAPSHOT"]
                 [tuddman/neocons "3.2.1-SNAPSHOT"]
                 [rid3 "0.2.1"]
                 [hickory "0.7.1"]]

  :repositories {"jitpack.io" "https://jitpack.io"}

  :min-lein-version "2.0.0"
  
  ;; .e.g: ` export JRI_PATH=/home/clojure/R/x86_64-pc-linux-gnu-library/3.4/rJava/jri/ `
  :jvm-opts [~(str "-Dclojure.server.repl={:port "
                   (let [num (+ (rand-int 1000) 7000)]
                     (println (str :start_socket_ num))
                     (if (= (get (System/getenv) (name :OS_TYPE)) (name :MACOX))
                       num
                       5555))
                   " :accept clojure.core.server/repl}")
             "-server" "-Dconf=.lein-env"
             ~(str "-Djava.library.path=" (get (System/getenv) "JRI_PATH") ":"
                   (System/getProperty "java.library.path"))]
  ;; => https://clojure.org/reference/repl_and_main => ` telnet 127.0.0.1 5555 ` , `:repl/quit`
  ;; 推荐 => ` echo "(prn 1111111111) (System/exit 0) :repl/quit " | netcat localhost 7870 `
  :source-paths ["src/clj" "src/cljc"]
  :test-paths ["test/clj"]
  :resource-paths ~(concat
                    (map (fn [lis] (str (get (System/getenv) "TRANS_PATH") lis) )
                         (list "commons-beanutils-1.8.0.jar"
                               "commons-codec-1.9.jar"
                               "commons-collections-3.2.1.jar"
                               "commons-lang-2.5.jar"
                               "commons-logging-1.1.1.jar"
                               "commons-logging-1.2.jar"
                               "ezmorph-1.0.6.jar"
                               "fluent-hc-4.5.2.jar"
                               "httpclient-4.5.2.jar"
                               "httpclient-cache-4.5.2.jar"
                               "httpclient-win-4.5.2.jar"
                               "httpcore-4.4.4.jar"
                               "httpmime-4.5.2.jar"
                               "jna-4.1.0.jar"
                               "jna-platform-4.1.0.jar"
                               "json-lib-2.4-jdk15.jar"
                               "util_trans.jar"))
                    (map #(str (get (System/getenv) "JRI_PATH") %)
                         (list "JRI.jar"
                               "JRIEngine.jar"
                               "REngine.jar"
                               (if (= (get (System/getenv) "OS_TYPE") "MACOX")
                                 "libjri.jnilib"
                                 "libjri.so")))
                    ["resources" "target/cljsbuild"])
  
  :target-path "target/%s/"
  :main ^:skip-aot jimw-clj.core
  :migratus {:store :database :db ~(get (System/getenv) "DATABASE_URL")}

  :plugins [[lein-cprop "1.0.1"]
            [migratus-lein "0.4.7"]
            [lein-cljsbuild "1.1.5"]
            [lein-immutant "2.1.0"]]
  :clean-targets ^{:protect false}
  [:target-path [:cljsbuild :builds :app :compiler :output-dir] [:cljsbuild :builds :app :compiler :output-to]]
  :figwheel
  {:http-server-root "public"
   :nrepl-port 7003
   :server-port 3889
   :css-dirs ["resources/public/css"]
   :nrepl-middleware
   [cemerick.piggieback/wrap-cljs-repl cider.nrepl/cider-middleware]}
  

  :profiles
  {:uberjar {:omit-source true
             :prep-tasks ["compile" ["cljsbuild" "once" "min"]]
             :cljsbuild
             {:builds
              {:min
               {:source-paths ["src/cljc" "src/cljs" "env/prod/cljs"]
                :compiler
                {:output-to "target/cljsbuild/public/js/app.js"
                 :optimizations :advanced
                 :pretty-print false
                 :closure-warnings
                 {:externs-validation :off :non-standard-jsdoc :off}
                 :externs ["react/externs/react.js" "resources/public/js/autosize.ext.js" "resources/public/js/viz.ext.js" "resources/public/js/wordcloud.ext.js"]
                 :foreign-libs [{:file "resources/public/js/autosize.min.js"
                                 :provides ["myexterns.autosize"]}
                                {:file "resources/public/js/viz-lite.js"
                                 :provides ["myexterns.viz"]}
                                {:file "resources/public/js/wordcloud2.js"
                                 :provides ["myexterns.wordcloud"]}]}}}}
             
             
             :aot :all
             :uberjar-name "jimw-clj.jar"
             :source-paths ["env/prod/clj"]
             :resource-paths ["env/prod/resources"]}

   :dev           [:project/dev :profiles/dev]
   :test          [:project/dev :project/test :profiles/test]

   :project/dev  {:dependencies [[prone "1.1.4"]
                                 [ring/ring-mock "0.3.0"]
                                 [ring/ring-devel "1.5.1"]
                                 [pjstadig/humane-test-output "0.8.1"]
                                 [binaryage/devtools "0.9.4"]
                                 [com.cemerick/piggieback "0.2.2-SNAPSHOT"]
                                 [doo "0.1.7"]
                                 [figwheel-sidecar "0.5.10"]]
                  :plugins      [[com.jakemccrary/lein-test-refresh "0.19.0"]
                                 [lein-doo "0.1.7"]
                                 [lein-figwheel "0.5.10"]
                                 [org.clojure/clojurescript "1.9.521"]]
                  :cljsbuild
                  {:builds
                   {:app
                    {:source-paths ["src/cljs" "src/cljc" "env/dev/cljs"]
                     :figwheel {:on-jsload "jimw-clj.core/mount-components"}
                     :compiler
                     {:main "jimw-clj.app"
                      :asset-path "/js/out"
                      :output-to "target/cljsbuild/public/js/app.js"
                      :output-dir "target/cljsbuild/public/js/out"
                      :source-map true
                      :optimizations :none
                      :foreign-libs [{:file "resources/public/js/autosize.min.js"
                                      :provides ["myexterns.autosize"]}
                                     {:file "resources/public/js/viz-lite.js"
                                      :provides ["myexterns.viz"]}
                                     {:file "resources/public/js/wordcloud2.js"
                                      :provides ["myexterns.wordcloud"]}]
                      :externs ["resources/public/js/autosize.ext.js"]
                      :pretty-print true}}}}
                  
                  
                  
                  :doo {:build "test"}
                  :source-paths ["env/dev/clj"]
                  :resource-paths ["env/dev/resources"]
                  :repl-options {:init-ns user}
                  :injections [(require 'pjstadig.humane-test-output)
                               (pjstadig.humane-test-output/activate!)]}
   :project/test {:resource-paths ["env/test/resources"]
                  :cljsbuild
                  {:builds
                   {:test
                    {:source-paths ["src/cljc" "src/cljs" "test/cljs"]
                     :compiler
                     {:output-to "target/test.js"
                      :main "jimw-clj.doo-runner"
                      :optimizations :whitespace
                      :pretty-print true}}}}
                  
                  }
   :profiles/dev {}
   :profiles/test {}})
