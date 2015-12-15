(ns pigeon.client
  #?(:cljs (:require-macros [cljs.core.async.macros :refer [go go-loop]]))
  (:require #?(:cljs [cljs.core.async :as a :refer [<! >!]]
               :clj [clojure.core.async :as a :refer [<! >! go go-loop]])
                    [pigeon.protocols :as p]
                    [pigeon.transit :as t]
                    [pigeon.firebase :as fb]))

(defrecord Client [opts hub-ref state]
  p/Lifecycle
  (started [_]
    (:started @state))
  (started [this timeout]
    (go (let [[v _] (a/alts! [(p/started this) (a/timeout timeout)])] v)))
  (status [_]
    (:status @state))
  (start [this]

    ;; idempotent you can only start a system that is down
    (when (= (:status @state) :down)

      (swap! state assoc :status :starting)

      (let [;; refs
            s-ref (fb/child hub-ref :servers)

            ;; channels
            auth-ch (a/promise-chan)
            info-ch (a/promise-chan)
            info-off-ch (a/promise-chan)]

        ;; 1. runtime state
        (swap! state assoc :channels [auth-ch info-ch info-off-ch])

        ;; 2. authenticate
        (if-let [auth (:auth opts)]
          (fb/auth
            hub-ref
            auth
            (fn [err auth]
              (when err
                (a/close! auth-ch))
              (when-not err
                (swap! state assoc :auth auth)
                (a/put! auth-ch true))))
          (a/put! auth-ch true))

        ;; 3. attached listener for server info
        (let [handler (fb/on-value
                        s-ref
                        (fn [ss]
                          (swap! state assoc :servers ss)
                          (a/put! info-ch true))
                        (fn [_] (a/close! info-off-ch)))]
          (go
            (<! info-off-ch)
            (fb/off-value s-ref handler)
            (p/stop this)))

        ;; if all channels report values in under 5s the client is up
        (go
          (let [t-ch (a/timeout 5000)
                a-ch (a/map (fn [& args] (every? true? args))
                            [auth-ch info-ch])]
            (if (= [true a-ch] (a/alts! [a-ch t-ch]))
              (do
                (swap! state assoc :status :up)
                (>! (:started @state) true))
              (p/stop this)))))

      :starting))

  (stop [this]

    ;; idempotent. you can only stop a system that is :up or :starting
    (when (#{:up :starting} (:status @state))

      (swap! state assoc :status :shutting-down)

      (let [{[auth-ch info-ch info-off-ch] :channels} @state]

        ;; 3. remove listeners for server info
        (a/close! info-off-ch)
        (a/close! info-ch)

        ;; 2. un-authenticate
        (fb/unauth hub-ref)
        (a/close! auth-ch)

        ;; 1. runtime state
        (a/close! (:started @state))
        (swap! state assoc
               :channels nil
               :servers nil
               :status :down
               :started (a/promise-chan))

        :down)))

  p/IRequest
  (request [_ val]
    (let [servers (fb/val (:servers @state))
          s-key (-> servers keys rand-nth name)
          m-ref (fb/push (fb/child hub-ref :queues s-key))
          r-ref (fb/child m-ref :responses)
          payload (t/write val)
          xform (map #(-> % fb/val :payload t/read))
          resp-ch (a/chan 10 xform)
          off-ch (a/promise-chan)
          on-cb #(a/put! resp-ch %)
          err-cb (fn [_] (a/close! off-ch))
          handler (fb/on-child-added r-ref on-cb err-cb)]
      (go
        (<! off-ch)
        (fb/off-child-added r-ref handler)
        (a/close! resp-ch)
        (fb/set m-ref nil))
      (fb/set-on-disconnect m-ref nil)
      (fb/set m-ref {:request {:payload payload}})
      [resp-ch off-ch])))

(defn client [{:keys [root-url path] :as opts}]
  (map->Client {:opts      opts
                :hub-ref   (fb/child (fb/ref root-url) path)
                :state     (atom {:status :down
                                  :started (a/promise-chan)})}))
