(ns pigeon.test-runners.node
  (:require [pigeon.tests]
            [cljs.test :refer-macros [run-tests]]
            [cljs.nodejs :as nodejs]))

(nodejs/enable-util-print!)

(defn -main [& args]
  (run-tests 'pigeon.tests))

(set! *main-cli-fn* -main)
