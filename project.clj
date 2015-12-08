(defproject pigeon "0.1.0-SNAPSHOT"
  :description "Message routing over firebase"

  :repositories [["clojars" {:sign-releases false}]]
  :jvm-opts ^:replace ["-Xms512m" "-Xmx512m" "-server"]

  :dependencies [[org.clojure/clojure "1.7.0" :scope "provided"]
                 [org.clojure/clojurescript "1.7.170" :scope "provided"]
                 [org.clojure/core.async "0.2.374"]
                 [com.cognitect/transit-clj "0.8.285"]
                 [com.cognitect/transit-cljs "0.8.232"]
                 [com.firebase/firebase-client-jvm "2.4.1"]
                 [cljsjs/firebase "2.3.1-0"]]

  :plugins [[lein-cljsbuild "1.1.1"]]

  :jar-exclusions [#".DS_Store"]
  :clean-targets ^{:protect false} ["target" "resources/public/compiled-js"]

  :profiles {:dev {:source-paths ["src" "dev"]
                   :repl-options {:init-ns pigeon.dev}}}

  :cljsbuild
    {:builds [{:id "dev"
               :source-paths ["src" "dev"]
               :compiler {:main pigeon.dev
                          :asset-path "compiled-js"
                          :output-to "resources/public/compiled-js/main.js"
                          :output-dir "resources/public/compiled-js"}}]})
