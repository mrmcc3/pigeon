(defproject mrmcc3/pigeon "0.1.0-SNAPSHOT"
  :description "Message routing over firebase"
  :url "http://github.com/mrmcc3/pigeon"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :repositories [["clojars" {:sign-releases false}]]

  :java-source-paths ["src/java"]

  :dependencies [[org.clojure/clojure "1.7.0" :scope "provided"]
                 [org.clojure/clojurescript "1.7.170" :scope "provided"]
                 [org.clojure/core.async "0.2.374" :scope "provided"]
                 [com.cognitect/transit-clj "0.8.285"]
                 [com.cognitect/transit-cljs "0.8.232"]
                 [com.firebase/firebase-client-jvm "2.4.1"]
                 [cljsjs/firebase "2.3.1-0"]
                 [environ "1.0.1"]]

  :plugins [[lein-cljsbuild "1.1.1"]]

  :jar-exclusions [#".DS_Store" #"dev" #"public" #"test"]

  :clean-targets ^{:protect false} ["target" "resources/public/compiled-js"]

  :profiles {:dev {:source-paths ["src" "dev"]}}

  :cljsbuild
    {:builds [{:id "browser"
               :source-paths ["src" "dev"]
               :compiler {:main pigeon.dev
                          :asset-path "compiled-js/dev"
                          :output-to "resources/public/compiled-js/main.js"
                          :output-dir "resources/public/compiled-js/browser"}}
              {:id "browser-test"
               :source-paths ["src" "test"]
               :compiler {:main pigeon.test-runners.browser
                          :asset-path "compiled-js/browser-test"
                          :output-to "resources/public/compiled-js/main.js"
                          :output-dir "resources/public/compiled-js/browser-test"}}]})
