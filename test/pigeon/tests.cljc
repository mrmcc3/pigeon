(ns pigeon.tests
  #?(:cljs (:require-macros [cljs.core.async.macros :refer [go go-loop]]))
  (:require
    #?(:clj  [clojure.core.async :as a :refer [<! >! >!! <!! chan close! go go-loop timeout]]
       :cljs [cljs.core.async :as a :refer [<! >! chan close! timeout]])
    #?(:clj  [clojure.test :refer [deftest is run-tests]]
       :cljs [cljs.test :refer-macros [deftest is run-tests async]])
    [pigeon.core :as p]
    [pigeon.env :refer [env]]))

(defn wait
  "helper for async tests. default timeout is 4s."
  ([ch] (wait ch (timeout 4000)))
  ([ch tch]
   (let [res (go
               (let [[_ c] (a/alts! [ch tch])]
                 (when (= c tch)
                   (is false "test timeout failure"))))]
     #?(:clj  (<!! res)
        :cljs (async done (go (<! res) (done)))))))

(deftest firebase-permission-fail
  (let [done (chan)
        opts {:root-url (:fb-root env)
              :location "bad-location"}
        handler (p/handler opts)
        client (p/client opts)]
    (go
      (is (= nil (<! handler)))
      (is (= nil (<! client)))
      (close! done))
    (wait done (timeout 10000))))

(deftest start-handler-and-client
  (let [done (chan)
        opts {:root-url (:fb-root env)
              :location (str (:fb-queue env) "/" (rand-int 1000000))}
        handler (p/handler opts)
        client (p/client opts)]
    (go
      (is (= true (<! handler)))
      (is (= true (<! client)))
      (close! done))
    (wait done)))
