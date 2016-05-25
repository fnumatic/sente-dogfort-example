(defproject com.taoensso.examples/sente "1.8.0-beta1"
  :description "Sente, node.js, DogFort, Figwheel web-app example project"
  :license {:name         "Eclipse Public License"
            :url          "http://www.eclipse.org/legal/epl-v10.html"
            :distribution :repo
            :comments     "Same as Clojure"}
  :min-lein-version "2.3.3"
  :global-vars {*warn-on-reflection* true
                *assert*             true}

  :dependencies
  [
   [org.clojure/clojure       "1.8.0"]

   [org.clojure/clojurescript "1.8.51"]
   [org.clojure/core.async    "0.2.374"]

   [sablono "0.7.1"]
   [org.omcljs/om "1.0.0-alpha34"]

   [com.taoensso/sente        "1.8.1"] ; <--- Sente
   [com.taoensso/timbre       "4.4.0-alpha1"]
   [figwheel-sidecar "0.5.3-1" :exclusions [clj-time joda-time org.clojure/tools.reader]]

   [dogfort         "0.2.0-SNAPSHOT"]]



  :npm
  {:dependencies
   [[source-map-support       "*"]



    ;; ws is needed for dogfort and express
    [ws                       "1.1.0"]]}

  :plugins
  [
   [lein-cljsbuild      "1.1.3"]
   [lein-shell          "0.5.0" :exclusions [org.clojure/clojure]]
   [lein-npm            "0.6.1" :exclusions [org.clojure/clojure]]
   [lein-figwheel       "0.5.3-1" :exclusions [org.clojure/clojure org.clojure/clojurescript]]]

  :source-paths ["src-server" "src-client"]

  :clean-targets ^{:protect false} ["resources/public/js/out"
                                      "resources/public/js/main.js"
                                      "target/out"
                                      "target/main.js"]

  :cljsbuild
  {:builds
   [{:id           :cljs-server
     :source-paths ["src-server"]
     :figwheel     true
     :compiler     {:main          "example.server"
                    :output-to     "target/main.js"
                    :output-dir    "target/out"
                    :target        :nodejs
                    ;;:optimizations :simple
                    ;;:source-map "target/main.js.map"
                    :optimizations :none
                    :source-map    true
                    :pretty-print  true}}

    {:id           :cljs-client
     :source-paths ["src-client"]
     :figwheel     true
     :compiler     {:main          example.client
                    :output-to     "resources/public/js/main.js"
                    :output-dir    "resources/public/js/out"
                    :asset-path    "js/out"
                    :source-map    true
                    :optimizations :none
                    :pretty-print  true}}]}

  ;; Call `lein start-repl` to get a (headless) development repl that you can
  ;; connect to with Cider+emacs or your IDE of choice:
  :aliases
  {"start" ["do" "cljsbuild" "once," "shell" "node" "target/main.js"]
   "fw" ["do" ["npm" "install"]
              ["figwheel" "cljs-server" "cljs-client"]]
   "node" ["shell" "node" "target/main.js"]
   "ancient" ["update-in" ":plugins" "conj" "[lein-ancient \"0.6.8\"]""--" "ancient"]})



