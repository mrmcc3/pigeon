(ns pigeon.protocols)

(defprotocol Lifecycle
  (started [this] [this timeout])
  (status [this])
  (start [this])
  (stop [this]))

(defprotocol IRequest
  (request [this val]))

(defprotocol IServe
  (request-ch [this]))
