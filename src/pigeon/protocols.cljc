(ns pigeon.protocols)

(defprotocol Lifecycle
  (status-ch [this])
  (status [this])
  (start [this])
  (stop [this]))

(defprotocol IRequest
  (request [this val]))

(defprotocol IServe
  (request-ch [this]))