(ns ^:figwheel-no-load jimw-clj.app
  (:require [jimw-clj.core :as core]
            [devtools.core :as devtools]))

(enable-console-print!)

(devtools/install!)

(core/init!)
