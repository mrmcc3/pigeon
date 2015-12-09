(ns pigeon.test-runners.browser
  (:require [pigeon.tests]
            [cljs.test :refer-macros [run-tests]]))

(enable-console-print!)

(run-tests 'pigeon.tests)
