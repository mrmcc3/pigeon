(ns pigeon.firebase
  (:refer-clojure :exclude [ref set update key val])
  (:require [clojure.walk :as walk])
  (:import [com.firebase.client
            Firebase$CompletionListener
            ValueEventListener
            ChildEventListener
            Firebase]))

;; WIP minimal clojure wrapper for firebase JVM client library

;; ----------------------------------------------------------------------------

(defn ref [s]
  (Firebase. s))

(defn key [r]
  (.getKey r))

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

(defn cb->complete [cb]
  (reify Firebase$CompletionListener
    (onComplete [_ err _] (cb err))))

(defn set
  ([r v]    (.setValue r (walk/stringify-keys v)))
  ([r v cb] (.setValue r (walk/stringify-keys v) (cb->complete cb))))

(defn update
  ([r v]    (.updateChildren r (walk/stringify-keys v)))
  ([r v cb] (.updateChildren r (walk/stringify-keys v) (cb->complete cb))))

(defn remove-on-disconnect
  ([r]      (.removeValue (.onDisconnect r)))
  ([r cb]   (.removeValue (.onDisconnect r) (cb->complete cb))))

;; ----------------------------------------------------------------------------

(defn callbacks->el [callbacks]
  (if-let [cb (:value callbacks)]
    (reify ValueEventListener
      (onDataChange [_ ss]
        (cb ss))
      (onCancelled [_ err]
        (when-let [cbe (:error callbacks)] (cb err))))
    (reify ChildEventListener
      (onChildAdded [_ ss _]
        (when-let [cb (:child-added callbacks)] (cb ss)))
      (onChildChanged [_ ss _]
        (when-let [cb (:child-changed callbacks)] (cb ss)))
      (onChildMoved [_ ss _]
        (when-let [cb (:child-moved callbacks)] (cb ss)))
      (onChildRemoved [_ ss]
        (when-let [cb (:child-removed callbacks)] (cb ss)))
      (onCancelled [_ err]
        (when-let [cb (:error callbacks)] (cb err))))))

(defn on-value
  ([r cb]
   (.addValueEventListener r
     (callbacks->el {:value cb})))
  ([r cb err-cb]
   (.addValueEventListener r
     (callbacks->el {:value cb :error err-cb}))))

(defn on-child-added
  ([r cb]
   (.addChildEventListener r
     (callbacks->el {:child-added cb})))
  ([r cb err-cb]
   (.addValueEventListener r
     (callbacks->el {:child-added cb :error err-cb}))))

;; todo child-changed child-moved child-removed

;; ----------------------------------------------------------------------------

(defprotocol ConvertibleToClojure
  (->clj [o]))

(extend-protocol ConvertibleToClojure
  java.util.HashMap
  (->clj [o]
    (let [entries (.entrySet o)]
      (reduce
        (fn [m [^String k v]]
          (assoc m (keyword k) (->clj v)))
        {} entries)))
  java.util.List
  (->clj [o] (into [] (map ->clj) o))
  java.lang.Object
  (->clj [o] o)
  nil
  (->clj [_] nil))

(defn val [ss]
  (-> ss .getValue ->clj))
