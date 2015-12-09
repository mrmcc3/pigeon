(ns test
  (:require [clojure.test :refer :all]))

(deftest equals-test
  (are [a b] (= a b)
             3 3))

(run-tests)