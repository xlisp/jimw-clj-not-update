(ns jimw-clj.doo-runner
  (:require [doo.runner :refer-macros [doo-tests]]
            [jimw-clj.core-test]))

(doo-tests 'jimw-clj.core-test)

