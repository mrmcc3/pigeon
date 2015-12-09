(ns pigeon.env
  (:require-macros [pigeon.macros :refer [compile-time-env]])
  (:require [clojure.string :as str]
            [cljs.reader :refer [read-string]]))

;; nodejs support adapted from https://github.com/weavejester/environ

(defn- keywordize [s]
  (-> (str/lower-case s)
      (str/replace "_" "-")
      (str/replace "." "-")
      (keyword)))

(defn- sanitize [k]
  (let [s (keywordize (name k))]
    (when-not (= k s)
      (println "Warning: environ key " k " has been corrected to " s))
    s))

(defn- read-system-env []
  (->> (.-env cljs.nodejs/process)
       (.stringify js/JSON) (.parse js/JSON) ;; hack
       js->clj
       (map (fn [[k v]] [(keywordize k) v]))
       (into {})))

(defn- read-env-file []
  (let [fs #_(cljs.nodejs/require "fs") 1
        contents (try
                   (.readFileSync fs ".lein-env" "utf8")
                   (catch :default e nil))]
    (when contents
      (into {}
        (map (fn [[k v]] [(sanitize k) v]))
        (read-string contents)))))

(defonce env
  (case cljs.core/*target*
    "nodejs" (merge (read-env-file) (read-system-env))
    (compile-time-env)))
