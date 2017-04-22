(defproject directionalsurvey "0.1.0-SNAPSHOT"
  :description "FIXME: write this!"
  :url "http://example.com/FIXME"
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/clojurescript "1.9.229"]
                 [http-kit "2.1.18"]
                 [ring "1.2.2"]
                 [ring/ring-json "0.3.1"]
                 [compojure "1.1.8"]
                 [com.taoensso/sente "0.14.0"]
                 [org.omcljs/om "1.0.0-alpha34"]
                 [org.clojure/tools.logging "0.2.6"]
                 [ch.qos.logback/logback-classic "1.1.2"]
                 [com.walmartlabs/lacinia "0.13.0"]
                 [org.clojure/core.async "0.1.303.0-886421-alpha"]
                 [com.datomic/datomic-free "0.9.5390" :exclusions [org.slf4j/slf4j-nop
                                                                   org.slf4j/slf4j-log4j12]]]
  :plugins [[lein-cljsbuild "1.1.4"]
            [lein-ring "0.8.10"]]

  :min-lein-version "2.5.3"

  :source-paths ["src/clj"]

  :clean-targets ^{:protect false} ["resources/public/js/compiled" "target"]

  :figwheel {:css-dirs ["resources/public/css"]}

  :profiles
  {:dev
   {:dependencies [[binaryage/devtools "0.8.2"]]
    :plugins      [[lein-figwheel "0.5.7"]]}}

  :cljsbuild { 
              :builds [{:id           "dev"
                        :source-paths ["src/cljs"]
                        :figwheel     {:on-jsload "directionalsurvey.core/mount-root"}
                        :compiler     {:main                 directionalsurvey.core
                                       :output-to            "resources/public/js/compiled/app.js"
                                       :output-dir           "resources/public/js/compiled/out"
                                       :asset-path           "js/compiled/out"
                                       :source-map-timestamp true
                                       :preloads             [devtools.preload]
                                       :external-config      {:devtools/config {:features-to-install :all}}}}
                       {:id           "min"
                        :source-paths ["src/cljs"]
                        :compiler     {:main            directionalsurvey.core
                                       :output-to       "resources/public/js/compiled/app.js"
                                       :optimizations   :advanced
                                       :closure-defines {goog.DEBUG false}
                                       :pretty-print    false}}]}
  :main directionalsurvey.server)


