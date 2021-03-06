(defproject grafter/grafter "0.3.0-SNAPSHOT"
  :description "Tools for the hard graft of data processing"
  :url "http://grafter.org/"
  :license {:name "Eclipse Public License - v1.0"
            :url "https://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.openrdf.sesame/sesame-runtime "2.7.14"

                  ;; For some reason there appears to be a weird
                  ;; version conflict with this sesame library.  So
                  ;; exclude it, as we're not using it.

                  :exclusions [org.openrdf.sesame/sesame-repository-manager]]

                 [org.clojure/algo.monads "0.1.5"]
                 [org.clojure/tools.logging "0.2.6"]
                 [clj-time "0.7.0"]
                 [clojure-csv/clojure-csv "2.0.1"]
                 [com.outpace/clj-excel "0.0.6" :exclusions [commons-codec]]
                 [me.raynes/fs "1.4.4"]
                 [org.marianoguerra/clj-rhino "0.2.1"]
                 [potemkin "0.3.4"]
                 [incanter/incanter-core "1.5.5" :exclusions [net.sf.opencsv/opencsv commons-codec]]
                 [net.sf.corn/corn-cps "1.1.7"]
                 [com.novemberain/pantomime "2.3.0"]]


  :codox {:defaults {:doc "FIXME: write docs"}
          :output-dir "api-docs"
          :src-dir-uri "http://github.com/Swirrl/grafter/blob/master/"
          :src-linenum-anchor-prefix "L"}

  :source-paths ["src/rdf-repository" "src/tabular" "src/templater" "src/rdf-common" "src/ontologies"
                 "src/pipeline"]
  :jvm-opts ["-Dapple.awt.UIElement=true"] ; Prevent Java process
                                        ; from appearing as a GUI
                                        ; app in OSX when Swing
                                        ; classes are loaded.

  :pedantic? :abort

  :repack [{:subpackage "rdf.common"
            :dependents #{"templater"}
            :path "src/rdf-common"}
           {:subpackage "templater"
            :path "src/templater"}
           {:type :clojure
            :path "src/ontologies"
            :levels 2}
           {:type :clojure
            :path "src/pipeline"
            :levels 2}
           {:type :clojure
            :path "src/rdf-repository"
            :levels 1}
           {:type :clojure
            :path "src/tabular"
            :levels 2}
           ]

  :profiles {:uberjar {:aot :all}

             :dev {:plugins [[com.aphyr/prism "0.1.1"] ;; autotest support simply run: lein prism
                             [codox "0.8.10"]
                             [org.clojars.rickmoynihan/lein-repack "0.2.5-SNAPSHOT" :exclusions [org.clojure/clojure
                                                                                                 org.codehaus.plexus/plexus-utils]]]

                   :dependencies [[com.aphyr/prism "0.1.1"]]

                   :env {:dev true}}})
