(ns pigeon.tests
  #?(:cljs (:require-macros [cljs.core.async.macros :refer [go]]))
  (:require
    #?(:clj  [clojure.core.async :as a :refer [<! >! >!! <!! go chan]]
       :cljs [cljs.core.async :as a :refer [<! >! chan]])
    #?(:clj  [clojure.test :refer [deftest is run-tests]]
       :cljs [cljs.test :refer-macros [deftest is run-tests async]])
             [pigeon.core :as p]
             [pigeon.env :refer [env]]
             [pigeon.firebase :as fb]))

(defn wait
  "helper for async tests. default timeout is 4s."
  ([ch] (wait ch (a/timeout 4000)))
  ([ch tch]
   (let [res (go
               (let [[_ c] (a/alts! [ch tch])]
                 (when (= c tch)
                   (is false "test timeout failure"))))]
     #?(:clj  (<!! res)
        :cljs (async done (go (<! res) (done)))))))

;; --------------------------------------------------------------------------

;; environment helper functions

(defn set-up-environment[servers clients path]
  (let [environment (atom {})]
    (swap! environment assoc
           :servers
           (map (fn [_] (p/server {:root-url (env :fb-root)
                                   :path     path}))
                (range servers)))
    (swap! environment assoc
           :clients
           (map (fn [_] (p/client {:root-url (env :fb-root)
                                   :path     path}))
                (range clients)))
    environment))

(defn start-group[environment start-me]
  (let [chs (map
              (fn[s] (p/start s) (p/started s))
              (start-me @environment))]
    (a/map (fn [& args] (every? true? args)) chs)))

(defn stop-group[environment stop-me]
  (doseq [s (stop-me @environment)]
    (p/stop s)))

(defn start-environment[e]
  (a/map (fn [& args] (every? true? args))
         [(start-group e :servers)
          (start-group e :clients)]))

(defn stop-environment[e]
  (stop-group e :servers)
  (stop-group e :clients))

;; --------------------------------------------------------------------------

;; tests

(deftest environment-atom
  (let [sn 1 cn 1
        e (set-up-environment sn cn "tq-tests/environment-atom")
        fbr (:hub-ref (first (:servers @e)))
        fbchan (a/chan)
        done (a/chan)]
    (go
      (<! (start-environment e))
      (is (= sn (count (:servers @e)))
          "There should be one server in the atom")
      (is (= cn (count (:clients @e)))
          "There should be one client in the atom")

      (fb/once-value fbr
                     (fn[ss] (a/put! fbchan (fb/val ss))))

      (is (= sn (count (<! fbchan)))
          "There should be one server on Firebase")

      (stop-environment e)
      (a/close! done))
    (wait done (a/timeout 10000))))

(deftest one-message
  (let [sn 1 cn 1
        msg "testing one message"
        e (set-up-environment sn cn "tq-tests/one-message")
        fbr (:hub-ref (first (:servers @e)))
        c (first (:clients @e))
        fbchan (a/chan)
        done (a/chan)]
    (go
      (<! (start-environment e))

      (p/request c msg)

      (fb/once-value fbr
                     (fn[ss] (a/put! fbchan (fb/val ss))))

      (let [res (first (vals (:queues (<! fbchan))))]
        (is (= 1 (count res))
            "There should be one request on Firebase"))

      (is (= msg (:payload (<! (p/request-ch (first (:servers @e))))))
          "The sending & recieving messages do not match")

      (stop-environment e)
      (a/close! done))
    (wait done (a/timeout 10000))))

(deftest many-messages
  (let [sn 1 cn 1 msgn 5
        msg "testing many messages"
        e (set-up-environment sn cn "tq-tests/many-messages")
        fbr (:hub-ref (first (:servers @e)))
        c (first (:clients @e))
        fbchan (a/chan)
        done (a/chan)]
    (go
      (<! (start-environment e))

      (doseq [i (range 0 msgn)]
        (p/request c (str msg i)))

      (fb/once-value fbr
                     (fn[ss] (a/put! fbchan (fb/val ss))))

      (let [res (first (vals (:queues (<! fbchan))))]
        (is (= msgn (count res))
            "There is an incorrect number of requests on Firebase"))

      (doseq [i (range 0 msgn)]
        (is (= (str msg i) (:payload (<! (p/request-ch (first (:servers @e))))))
            "The sending & recieving messages do not match"))

      (stop-environment e)
      (a/close! done))
    (wait done (a/timeout 10000))))

(deftest clean-up
  (let [sn 1 cn 1
        msg "testing clean up"
        e (set-up-environment sn cn "tq-tests/clean-up")
        fbr (:hub-ref (first (:servers @e)))
        c (first (:clients @e))
        fbchan (a/chan)
        done (a/chan)]
    (go
      (<! (start-environment e))

      (p/request c msg)

      (fb/once-value fbr
                     (fn[ss] (a/put! fbchan (fb/val ss))))
      (is (not (nil? (first (vals (:queues (<! fbchan))))))
          "The message should have been sent to Firebase")

      (stop-environment e)

      (<! (a/timeout 1000))

      (fb/once-value fbr
                     (fn[ss] (if (nil? (fb/val ss))
                               (a/put! fbchan msg))))
      (is (= msg (<! fbchan))
          "The Firebase has some garbled data remaining")

      (a/close! done))
    (wait done (a/timeout 15000))))

(deftest load-balance
  (let [sn 6 cn 1 msgn (* 5 sn) ;; msg n must be high enough
        msg "testing load balance"
        e (set-up-environment sn cn "tq-tests/load-balance")
        c (first (:clients @e))
        fbr (:hub-ref (first (:servers @e)))
        fbchan (a/chan)
        done (a/chan)]
    (go
      (<! (start-environment e))

      (doseq [i (range 0 msgn)]
               (p/request c msg))

      (fb/once-value fbr
                     (fn[ss] (a/put! fbchan (fb/val ss))))
      (is (= sn (count (:queues (<! fbchan))))
          "Not enough load balancing is occuring")

      (stop-environment e)
      (a/close! done))
    (wait done (a/timeout (* sn 300)))))

(deftest request-response
  (let [sn 1 cn 2
        e (set-up-environment sn cn "tq-tests/request-response")
        rec "msgrecd"
        done (a/chan)]
    (go
      (<! (start-environment e))

      (let [res (p/request (first (:clients @e)) {:msg {:type :lead
                                                        :data "very much data"}})
            {:keys [payload resp-ch]} (<! (p/request-ch (first (:servers @e))))]
        (>! resp-ch rec)
        (is (= rec (<! (first res)))))

      (stop-environment e)
      (a/close! done))
    (wait done (a/timeout (* 10000)))))

(deftest request-responses
  (let [sn 1 cn 2 msgn 20
        e (set-up-environment sn cn "tq-tests/request-responses")
        rec "msgrecd"
        done (a/chan)]
    (go
      (<! (start-environment e))

      (let [res (p/request (first (:clients @e)) {:msg {:type :lead
                                                        :data "very much data"}})
            {:keys [payload resp-ch]} (<! (p/request-ch (first (:servers @e))))]
        (doseq [i (range 0 msgn)]
          (>! resp-ch (str rec i))
          (is (= (str rec i) (<! (first res))))))

      (stop-environment e)
      (a/close! done))
    (wait done (a/timeout (* 10000)))))

(comment
  )