(defproject cl-mud "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]]
  :main ^:skip-aot cl-mud.core
  :target-path "target/%s"
  :test-paths ["test" "test-resources"]
  :plugins [[quickie "0.2.5"]
            [lein-cloverage "1.0.2"]]
  :profiles {:uberjar {:aot :all}})
