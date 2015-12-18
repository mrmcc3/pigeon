(ns pigeon.env
  (:require [environ.core :as environ]))

(defonce env environ/env)

(defmacro compile-time-env [] env)
