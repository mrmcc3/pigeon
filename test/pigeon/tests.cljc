(ns pigeon.tests
  #?(:cljs (:require-macros [cljs.core.async.macros :refer [go go-loop]]
                            [cljs.env :refer [with-compiler-env]]))
  (:require
    [pigeon.core :as p]
    [pigeon.firebase :as fb]
    #?(:clj  [clojure.core.async :as a :refer [<! >! >!! <!! chan close! go go-loop]]
       :cljs [cljs.core.async :as a :refer [<! >! chan close!]])
    #?(:clj  [clojure.test :refer [deftest use-fixtures is are run-tests]]
       :cljs [cljs.test :refer-macros [deftest use-fixtures is are run-tests async]])))

;; helper for async tests
(defn wait [ch]
  #?(:clj (<!! ch)
     :cljs (async done (go (<! ch) (done)))))










;; TESTING

(def s (atom nil))

(defn start-system
  "Start up the servers and the clients"
  [test-fn]
  (let [root "https://totalquote.firebaseio.com/test-queue"
        q (str "q-" (rand-int 10000))]
    (swap! s assoc :h1 (p/handler {:root-url root
                                   :location q}))

    (swap! s assoc :c1 (p/client {:root-url root
                                  :location q})))
  (test-fn))

(defn start-large-system
  "Start up the servers and the clients"
  [test-fn]
  (let [root "https://totalquote.firebaseio.com/test-queue"
        q (str "q-" (rand-int 1000000))]
    (swap! s assoc :h1 (p/handler {:root-url root
                                   :location q}))
    (swap! s assoc :h2 (p/handler {:root-url root
                                   :location q}))
    (swap! s assoc :h3 (p/handler {:root-url root
                                   :location q}))

    (swap! s assoc :c1 (p/client {:root-url root
                                  :location q})))
  (test-fn))


;; Tests

(deftest server-up
  (let [done (chan)
        server (:h1 @s)]
    (go
      (let [res (<! server)]
        (is (instance? clojure.lang.PersistentArrayMap res)
            "The server should send an array map")
        (is (instance? com.firebase.client.Firebase (:qref res))
            "The server should have responded with the reference to its queue")
        (is (instance? com.firebase.client.Firebase (:sref res))
            "The server should have responded with the reference to its information")
        (is (= (fb/key (:qref res)) (fb/key (:sref res)))
            "The server should have created a reference to a queue under its ID")
        )
      (close! done))
    (wait done)))

(deftest client-up
  (let [done (chan)
        client (:c1 @s)]
    (go
      (let [res (<! client)]
        (is (instance? clojure.lang.PersistentArrayMap res)
            "The client should send an array map")
        (is (instance? com.firebase.client.Firebase (:sref res))
            "The client should have responded with the reference to the server it's connected to")
        )
      (close! done))
    (wait done)))

(deftest single-string-message
  (let [done (chan)
        msg-chan (chan)
        client (:c1 @s)
        server (:h1 @s)
        msg "this is a test message"]
    (go
      (<! client)
      (let [conn (<! server)]
        (is (= (fb/key (:qref conn)) (fb/key (:sref conn)))
            "The server should have created a reference to a queue under its ID")

        (>! client msg)
        (let [res (<! server)]
          (fb/once (:qref conn)
                   (fn[ss] (a/put! msg-chan (fb/val ss))))

          (is (= (count (<! msg-chan)) 1)
              "There should be one message on the queue")

          (is (= res msg)
              (str "Server should have recieved " msg))
          (close! done))))
    (wait done)))

(deftest single-map-message
  (let [done (chan)
        client (:c1 @s)
        server (:h1 @s)
        msg {:cool "beans" :anum 3434 :names ["gavan" "mikey"] :amap {"stringkey" "val"} :last "hey"}]
    (go
      (<! client)
      (<! server)

      (>! client msg)
      (let [res (<! server)]
        (is (= res msg)
            (str "Server should have recieved " msg)))
      (close! done))
    (wait done)))

(deftest double-string-message
  (let [done (chan)
        client (:c1 @s)
        server (:h1 @s)
        msg1 "this is a test message"
        msg2 "this is a second test message"]
    (go
      (<! client)
      (<! server)

      (>! client msg1)
      (>! client msg2)
      (let [res1 (<! server)
            res2 (<! server)]
        (are [x y] (= x y)
             res1 msg1
             res2 msg2))
      (close! done))
    (wait done)))

(deftest multi-mixed-message
  (let [done (chan)
        client (:c1 @s)
        server (:h1 @s)
        msg1 {:real "shiiiz"}
        msg2 "this is a second test message"
        msg3 [1 4 7 0]
        msg4 {:one "more" :for "the" :team "yea"}]
    (go
      (<! client)
      (<! server)

      (>! client msg1)
      (>! client msg2)
      (>! client msg3)
      (>! client msg4)
      (let [res1 (<! server)
            res2 (<! server)
            res3 (<! server)
            res4 (<! server)]
        (are [x y] (= x y)
             res1 msg1
             res2 msg2
             res3 msg3
             res4 msg4))
      (close! done))
    (wait done)))

(use-fixtures :each start-system)

(comment
  (run-tests))
