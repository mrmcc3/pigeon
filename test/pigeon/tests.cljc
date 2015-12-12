(ns pigeon.tests
  #?(:cljs (:require-macros [cljs.core.async.macros :refer [go]]))
  (:require
    #?(:clj  [clojure.core.async :as a :refer [<! >! >!! <!! go chan]]
       :cljs [cljs.core.async :as a :refer [<! >! chan]])
    #?(:clj  [clojure.test :refer [deftest is run-tests]]
       :cljs [cljs.test :refer-macros [deftest is run-tests async]])
    [pigeon.queue :as pq]
    [pigeon.firebase :as fb]
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

;; TODO


