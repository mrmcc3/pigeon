(ns pigeon.server
  #?(:cljs (:require-macros [cljs.core.async.macros :refer [go go-loop]]))
  (:require #?(:cljs [cljs.core.async :as a :refer [<! >!]]
               :clj [clojure.core.async :as a :refer [<! >! go go-loop]])
                    [pigeon.protocols :as p]
                    [pigeon.transit :as t]
                    [pigeon.firebase :as fb]))

;; ---------------------------------------------------------------------------
;; server

(defrecord Server [opts hub-ref status-ch state]
  p/Lifecycle
  (status-ch [_] status-ch)
  (status [_] (:status @state))

  (start [this]

    ;; idempotent. you can only start a system that is :down
    (when (= (p/status this) :down)

      (swap! state assoc :status :starting)

      (let [
            ;; refs
            q-ref (fb/push (fb/child hub-ref :queues))
            s-ref (fb/child hub-ref :servers (fb/key q-ref))

            ;; channels
            auth-ch (a/promise-chan)
            s-dis-ch (a/promise-chan)
            q-dis-ch (a/promise-chan)
            info-ch (a/promise-chan)

            reqs-ch (a/chan)
            reqs-off-ch (a/promise-chan)

            ]

        ;; 1. store runtime state (refs+channels) for shutdown
        (swap! state assoc
               :refs [q-ref s-ref]
               :channels [auth-ch s-dis-ch q-dis-ch
                          info-ch reqs-off-ch reqs-ch])

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
        ;; TODO use channels to flatten this out. decomplect FTW
        (let [handler (fb/on-child-added
                        q-ref
                        (fn [ss]
                          (let [resp-ch (a/chan)]
                            (a/put!
                              reqs-ch
                              (-> ss (fb/child :request) fb/val
                                  (update :payload t/read)
                                  (assoc :resp-ch resp-ch)))
                            (go-loop []
                              (when-let [resp (<! resp-ch)]
                                (println "respond: " resp)
                                (recur)))))
                        (fn [_] (a/close! reqs-off-ch)))]
          (go
            (<! reqs-off-ch)
            (fb/off-child-added q-ref handler)
            (p/stop this)))

        ;; 5. persist server info
        (fb/set
          s-ref
          {"online" true}
          (fn [err]
            (if err
              (a/close! info-ch)
              (a/put! info-ch true))))


        ;; if all channels report in under 5s then the server is up
        (go
          (let [t-ch (a/timeout 5000)
                a-ch (a/map (fn [& args] (every? true? args))
                            [auth-ch q-dis-ch s-dis-ch info-ch])]
            (if (= [true a-ch] (a/alts! [a-ch t-ch]))
              (do
                (swap! state assoc :status :up)
                (>! status-ch :up))
              (p/stop this))))

        :starting)))

  (stop [this]
    ;; idempotent. you can only stop a system that is :up or :starting
    (when (#{:up :starting} (p/status this))

      (swap! state assoc :status :shutting-down)

      ;; extract the runtime state
      (let [{[auth-ch s-dis-ch q-dis-ch info-ch reqs-off-ch reqs-ch]
             :channels
             [q-ref s-ref]
             :refs} @state]

        ;; 5. remove the server data
        (fb/set s-ref nil)
        (fb/set q-ref nil)
        (a/close! info-ch)

        ;; 4. remove child-added handler
        (a/close! reqs-off-ch)
        (a/close! reqs-ch)

        ;; 3. cancel onDisconnects
        (fb/cancel-on-disconnect q-ref)
        (fb/cancel-on-disconnect s-ref)
        (a/close! q-dis-ch)
        (a/close! s-dis-ch)

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

  p/IServe
  (request-ch [_] (get-in @state [:channels 5])))

(defn server [{:keys [root-url path] :as opts}]
  (map->Server
    {:opts      opts
     :hub-ref   (fb/child (fb/ref root-url) path)
     :status-ch (a/chan)
     :state     (atom {:status :down})}))
