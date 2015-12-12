(ns pigeon.queue
  #?(:cljs (:require-macros [cljs.core.async.macros :refer [go go-loop]]))
  (:require #?(:cljs [cljs.core.async :as a :refer [<! >!]]
               :clj [clojure.core.async :as a :refer [<! >! go go-loop]])
                    [pigeon.transit :as t]
                    [pigeon.firebase :as fb]))

;; ---------------------------------------------------------------------------
;; protocols

(defprotocol Lifecycle
  (status-ch [this])
  (status [this])
  (start [this])
  (stop [this]))

(defprotocol IRequest
  (request [this val]))

(defprotocol IServe
  (request-ch [this]))

;; ---------------------------------------------------------------------------
;; server

(defrecord Server [opts hub-ref status-ch state]
  Lifecycle
  (status-ch [_] status-ch)
  (status [_] (:status @state))

  (start [this]

    ;; idempotent. you can only start a system that is :down
    (when (= (status this) :down)

      ;; change status to starting
      (swap! state assoc :status :starting)
      (a/put! status-ch :starting)

      (let [
            ;; refs
            q-ref (fb/push (fb/child hub-ref "queues"))
            s-ref (fb/child hub-ref "servers" (fb/key q-ref))

            ;; channels
            auth-ch (a/promise-chan)
            s-dis-ch (a/promise-chan)
            q-dis-ch (a/promise-chan)
            on-err-ch (a/promise-chan)
            reqs-ch (a/chan)
            info-ch (a/promise-chan)

            ;; auth config
            {:keys [fb-token fb-secret]} opts
            auth-data (cond
                        fb-token
                        {:token fb-token}
                        fb-secret
                        {:token {:secret  fb-secret
                                 :options {:expire-in-days 10}
                                 :payload {:uid (fb/key q-ref)}}})]

        ;; 1. store runtime state (refs+channels) for shutdown
        (swap! state assoc
               :refs [q-ref s-ref]
               :channels [auth-ch s-dis-ch q-dis-ch
                          on-err-ch info-ch reqs-ch])

        ;; 2. authenticate
        (if auth-data
          (fb/auth
            hub-ref
            auth-data
            (fn [err auth]
              (when err
                (a/close! auth-ch))
              (when-not err
                (swap! state assoc :auth auth)
                (a/put! auth-ch true))))
          (a/put! auth-ch true))

        ;; 3. set onDisconnects
        (fb/set-on-disconnect
          q-ref
          nil
          (fn [err]
            (if err
              (a/close! q-dis-ch)
              (a/put! q-dis-ch true))))
        (fb/set-on-disconnect
          s-ref
          nil
          (fn [err]
            (if err
              (a/close! s-dis-ch)
              (a/put! s-dis-ch true))))


        ;; 4. attach child-added handler
        (let [handler (fb/on-child-added
                        q-ref
                        (fn [ss] (a/put! reqs-ch ss))
                        (fn [_] (a/close! on-err-ch)))]
          (go
            (<! on-err-ch)
            (fb/off-child-added q-ref handler)))

        ;; 5. persist server info
        (fb/set
          s-ref
          {"online" true}
          (fn [err]
            (if err
              (a/close! info-ch)
              (a/put! info-ch true))))

        ;; if any channels close then shutdown the server
        (a/go-loop []
          (let [[v _] (a/alts! [auth-ch q-dis-ch s-dis-ch info-ch on-err-ch])]
            (if v
              (recur)
              (stop this))))

        ;; if all channels report values in under 10s then the server is up
        (go
          (let [t-ch (a/timeout 10000)
                a-ch (a/map (fn [& args] (every? true? args))
                            [auth-ch q-dis-ch s-dis-ch info-ch])
                [v ch] (a/alts! [a-ch t-ch])]
            (cond
              (= ch t-ch) (stop this)
              v (do
                  (swap! state assoc :status :up)
                  (>! status-ch :up)))))

        :starting)))

  (stop [this]
    ;; idempotent. you can only stop a system that is :up or :starting
    (when (#{:up :starting} (status this))

      ;; prevent multiple stop calls
      (swap! state assoc :status :shutting-down)

      ;; extract the runtime state
      (let [{[auth-ch s-dis-ch q-dis-ch on-err-ch info-ch reqs-ch]
             :channels
             [q-ref s-ref]
             :refs} @state]

        ;; 5. remove the server info
        (fb/set s-ref nil)
        (a/close! info-ch)

        ;; 4. remove child-added handler
        (a/close! on-err-ch)
        (a/close! reqs-ch)

        ;; 3. cancel onDisconnects
        (fb/set q-ref nil)
        (fb/cancel-on-disconnect q-ref)
        (fb/cancel-on-disconnect s-ref)
        (a/close! s-dis-ch)
        (a/close! q-dis-ch)

        ;; 2. un-authenticate
        (fb/unauth hub-ref)
        (a/close! auth-ch)

        ;; 1. cleanup the runtime state and set status to :down
        (swap! state assoc
               :channels nil
               :refs nil
               :status :down)
        (a/put! status-ch :down)
        :down)))

  IServe
  (request-ch [_] (get-in @state [:channels 5])))

(defn server [{:keys [root-url path] :as opts}]
  (map->Server
    {:opts      opts
     :hub-ref   (fb/child (fb/ref root-url) path)
     :status-ch (a/chan)
     :state     (atom {:status :down})}))


;; ---------------------------------------------------------------------------
;; client

; (defrecord Client [opts]
;   Lifecycle
;   (status-ch [_])
;   (status [_])
;   (start [_])
;   (stop [_])
;
;   IRequest
;   (request [_ val]))
;
; (defn client [opts]
;   (map->Client {:opts opts}))
