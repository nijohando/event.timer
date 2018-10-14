(ns prj.user
  (:require [jp.nijohando.event.timer :as tm :include-macros true]
            [jp.nijohando.event.timer-test-cljs]
            [cljs.test :refer-macros [run-tests]]))

(defn test-cljs
  []
  (run-tests 'jp.nijohando.event.timer-test-cljs))
