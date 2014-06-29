(defproject clj-mud "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [clj-time "0.7.0"]
                 [aleph "0.3.2"]]
  :main ^:skip-aot clj-mud.core
  :target-path "target/%s"
  :test-paths ["test" "test-resources"]
  :plugins [[quickie "0.2.5"]
            [lein-cloverage "1.0.2"]
            [quickie "0.2.5"]]
  :profiles {:uberjar {:aot :all}})
