(ns pigeon.tests
  #?(:cljs (:require-macros [cljs.core.async.macros :refer [go go-loop]]
                            [cljs.env :refer [with-compiler-env]]))
  (:require
    [pigeon.core :as p]
    #?(:clj  [clojure.core.async :as a :refer [<! >! >!! <!! chan close! go go-loop]]
       :cljs [cljs.core.async :as a :refer [<! >! chan close!]])
    #?(:clj  [clojure.test :refer [deftest is run-tests]]
       :cljs [cljs.test :refer-macros [deftest is run-tests async]])))

;; helper for async tests
(defn wait [ch]
  #?(:clj (<!! ch)
     :cljs (async done (go (<! ch) (done)))))

(deftest example-test
  (is (= 1 1)))

(deftest example-async
 (let [done (chan)]
   (go
     (<! (a/timeout 1000))
     (is (= 1 1))
     (close! done))
   (wait done)))
