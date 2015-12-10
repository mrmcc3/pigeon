(ns pigeon.firebase
  (:refer-clojure :exclude [ref set update key val])
  (:require [cljsjs.firebase]))

;; WIP minimal clojurescript wrapper for firebase web client library

;; nodejs support?

;; ----------------------------------------------------------------------------

(defn ref [s]
  (js/Firebase. s))

(defn key [r]
  (.key r))

(defn push [r]
  (.push r))

(defn child [r & args]
  (reduce
    (fn [acc c]
      (.child acc
        (cond
          (keyword? c) (name c)
          :else c)))
    r args))

;; ----------------------------------------------------------------------------

(defn set [r v]
  (.set r (clj->js v)))

(defn update
  ([r v] (.update r (clj->js v)))
  ([r v cb] (.update r (clj->js v) #(cb %))))

(defn remove-on-disconnect [& refs]
  (doseq [r refs]
    (-> r .onDisconnect .remove)))

;; ----------------------------------------------------------------------------

(defn on-value
  ([r cb]
   (.on r "value" cb))
  ([r cb cb-err]
   (.on r "value" cb cb-err)))

(defn on-child-added [r cb]
  (.on r "child_added" cb))

;; ----------------------------------------------------------------------------

(defn val [ss]
  (-> ss .val (js->clj :keywordize-keys true)))
