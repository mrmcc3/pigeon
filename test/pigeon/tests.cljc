(ns pigeon.tests
  #?(:cljs (:require-macros [cljs.core.async.macros :refer [go]]))
  (:require
    #?(:clj  [clojure.core.async :as a :refer [<! >! >!! <!! go chan]]
       :cljs [cljs.core.async :as a :refer [<! >! chan]])
    #?(:clj  [clojure.test :refer [deftest is run-tests]]
       :cljs [cljs.test :refer-macros [deftest is run-tests async]])
    [pigeon.core :as p]
    [pigeon.env :refer [env]]))

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
  (let [env (atom {})]
    (swap! env assoc
           :servers
           (map (fn [_] (p/server {:root-url "https://totalquote.firebaseio.com/"
                                   :path     path}))
                (range servers)))
    (swap! env assoc
           :clients
           (map (fn [_] (p/client {:root-url "https://totalquote.firebaseio.com/"
                                   :path     path}))
                (range clients)))
    env))

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
  (let [sn 3 cn 3
        e (set-up-environment sn cn "tq-tests/environment-atom")
        done (a/chan)]
    (go
      (<! (start-environment e))
      (is (= sn (count (:servers @e)))
          "There should be one server in the atom")
      (is (= cn (count (:clients @e)))
          "There should be one client in the atom")
      (stop-environment e)
      (a/close! done))
    (wait done (a/timeout 10000))))

(deftest load-balance
  (let [sn 3 cn 1 msgn 10
        e (set-up-environment sn cn "tq-tests/single-through")
        c (first (:clients @e))
        done (a/chan)]
    (go
      (<! (start-environment e))

      (doseq [i (range msgn)]
        (p/request c "HEY THERE"))

      (stop-environment e)
      (a/close! done))
    (wait done (a/timeout 10000))))

(comment
  )