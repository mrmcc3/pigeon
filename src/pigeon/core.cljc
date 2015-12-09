(ns pigeon.core
  #?(:cljs (:require-macros [cljs.core.async.macros :refer [go go-loop]]))
  (:require #?(:cljs [cljs.core.async :as a :refer [<! >!]]
               :clj  [clojure.core.async :as a :refer [<! >! go go-loop]])
            [pigeon.transit :as t]
            [pigeon.firebase :as fb]
            #?(:cljs [pigeon.env])))

;; WIP async processes for message routing over firebase

(defn handler [{:keys [root-url location]}]
  (let [q-ref (fb/child (fb/ref root-url) location)
        subq-ref (-> q-ref (fb/child "queues") fb/push)
        subq-key (fb/key subq-ref)
        info-ref (-> q-ref (fb/child "servers" subq-key))
        started (a/promise-chan)
        msg-ch (a/chan)
        channel (a/chan)]
    (fb/remove-on-disconnect subq-ref)
    (fb/remove-on-disconnect info-ref)
    (fb/on-child-added
      subq-ref
      (fn [ss]
        (let [payload (fb/val ss)
              req (t/read payload)]
          (a/put! msg-ch req))))
    (fb/update
      info-ref
      {:online true}
      (fn [err]
        (when err
          (a/close! channel)
          (a/close! started)
          (a/close! msg-ch))
        (when-not err
          (a/put! started true))))
    (go []
        (when-let [v (<! started)]
          (>! channel v)
          (loop []
            (>! channel (<! msg-ch))
            (recur))))
    channel))

(defn client [{:keys [root-url location]}]
  (let [q-ref (fb/child (fb/ref root-url) location)
        servers-ref (-> q-ref (fb/child "servers"))
        servers (atom nil)
        started (a/promise-chan)
        channel (a/chan)]
    (fb/on-value
      servers-ref
      (fn [ss]
        (let [v (fb/val ss)]
          (reset! servers v)
          (when v (a/put! started true))))
      (fn [err]
        (when err
          (a/close! channel)
          (a/close! started))))
    (go
      (when-let [v (<! started)]
        (>! channel v)
        (loop []
          (let [req (<! channel)
                payload (t/write req)
                server (-> @servers keys rand-nth)
                msg-ref (fb/push (fb/child q-ref "queues" server))]
            (fb/set msg-ref payload))
          (recur))))
    channel))
