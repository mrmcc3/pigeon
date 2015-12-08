(ns pigeon.transit
  (:refer-clojure :exclude [read])
  (:require [cognitect.transit :as t])
  #?(:clj (:import [java.io ByteArrayOutputStream ByteArrayInputStream])))

;; Default transit read/write

#?(:cljs
   (def default-writer (t/writer :json)))

#?(:cljs
   (defn write [x] (t/write default-writer x)))

#?(:cljs
   (def default-reader (t/reader :json)))

#?(:cljs
   (defn read [x] (t/read default-reader x)))

#?(:clj
   (defn write [x]
     (let [baos (ByteArrayOutputStream.)]
       (t/write (t/writer baos :json) x)
       (.toString baos))))
#?(:clj
   (defn read [x]
     (-> (.getBytes x "UTF-8")
         ByteArrayInputStream.
         (t/reader :json)
         t/read)))
