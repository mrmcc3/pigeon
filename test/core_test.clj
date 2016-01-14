(ns clojure-test
  (:require [clojure.test :refer :all]
            [clojure.core.async :as a :refer [close! go go-loop <! >!]]
            [pigeon.core :as p]
            [pigeon.tests :refer [wait]]))

(def s (atom nil))

(defn start-system "Start up the servers and the clients"
  [test-fn]
  (let [root "https://totalquote.firebaseio.com/"
        q "core-test-queue"]
    (swap! s assoc :h1 (p/handler {:root-url root
                                   :location q}))
    (swap! s assoc :c1 (p/client {:root-url root
                             :location q})))
  (test-fn))

(use-fixtures :once start-system)

(run-tests)