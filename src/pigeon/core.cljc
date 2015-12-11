(ns pigeon.core
  #?(:cljs (:require-macros [cljs.core.async.macros :refer [go go-loop]]))
  (:require #?(:cljs [cljs.core.async :as a :refer [<! >!]]
               :clj  [clojure.core.async :as a :refer [<! >! go go-loop]])
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
    (when-not (= (status this) :up)

      (let [q-ref (fb/push (fb/child hub-ref "queues"))
            {:keys [fb-token fb-secret]} opts
            auth-data (cond
                        fb-token {:token fb-token}
                        fb-secret
                        {:token {:secret  fb-secret
                                 :payload {:uid (str "server:"
                                                     (fb/key q-ref))}
                                 :options {:expire-in-days 10}}})
            auth-ch (a/promise-chan)]

        ;; the server is up iff

        ;; 1. authenticated when auth-token/auth-secret is provided
        ;; 2. onDisconnects are in place
        ;; 3. child-added handler is in place
        ;; 4. server info persisted

        ;; calling start when the server is already up will have no affect

        ;(if auth-data
        ;  (fb/auth
        ;    hub-ref
        ;    auth-data
        ;    (fn [err auth]
        ;      (when err
        ;        (a/close! auth-ch))
        ;      (when-not err
        ;        (swap! state assoc :auth auth)
        ;        (a/put! auth-ch true))))
        ;  (a/put! auth-ch true))

        )))

  (stop [_]
    (swap! state assoc :status :down)
    (a/put! status-ch :down))

  IServe
  (request-ch [_] (get-in @state [:channels :request])))

(defn server [{:keys [root-url path] :as opts}]
  (map->Server
    {:opts opts
     :status-ch (a/chan)
     :hub-ref (fb/child (fb/ref root-url) path)
     :state (atom {:status :down})}))


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
