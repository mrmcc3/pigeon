(ns pigeon.macros
  (:require [environ.core :refer [env]]))

(defmacro compile-time-env [] env)
