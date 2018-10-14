(ns prj.user
  (:require [jp.nijohando.event.timer :as tm]
            [jp.nijohando.prj.test :as prj-test]
            [prj.cljs]))

(defn test-clj
  []
  (prj-test/run-tests 'jp.nijohando.event.timer-test-clj))

(defn test-cljs
  []
  (prj.cljs/test-cljs))

(defn repl-cljs
  []
  (prj.cljs/repl-cljs))
