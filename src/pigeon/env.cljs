(ns pigeon.env
  (:require-macros [pigeon.env :refer [compile-time-env]]))

;; nodejs support?

(defonce env (compile-time-env))
