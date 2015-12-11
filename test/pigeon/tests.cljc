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

;(deftest firebase-permission-fail
;  (let [done (chan)
;        opts {:root-url (:fb-root env)
;              :path "bad-location"}
;        server (p/server opts)]
;        ; client (p/client opts)]
;    (go
;      (p/start server)
;      (is (= :down (<! (p/status-ch server))))
;      (is (= :down (p/status server)))
;      ; (is (= :down (<! (p/changes client))))
;      ; (is (= :down (p/status client)))
;      (a/close! done))
;    (wait done (a/timeout 10000))))

;; calling started when the client/server is already up will have no affect

