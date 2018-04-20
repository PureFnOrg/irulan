(defproject org.purefn/irulan "0.3.2-SNAPSHOT"
  :description "Definition and specs of events and commands for the org.purefn event sourcing approach."
  :url "https://github.com/PureFnOrg/irulan"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0-alpha16"]
                 [org.clojure/test.check "0.9.0"]
                 [commons-validator "1.6"]
                 [com.gfredericks/test.chuck "0.2.7"]
                 [com.taoensso/timbre "4.10.0"]]

  :profiles {:dev {:dependencies [[org.clojure/tools.namespace "0.2.11"]]
                   :jvm-opts ["-Xmx2g"]
                   :source-paths ["dev"]
         :codeina {:sources ["src"]
                   :exclude [org.purefn.irulan.version]
                   :reader :clojure
                   :target "doc/dist/latest/api"
                   :src-uri "http://github.com/PureFnOrg/irulan/blob/master/"
                   :src-uri-prefix "#L"}
         :plugins [[funcool/codeina "0.4.0"
                    :exclusions [org.clojure/clojure]]
                   [lein-ancient "0.6.10"]]}}
  :aliases {"project-version" ["run" "-m" "org.purefn.irulan.version"]})
