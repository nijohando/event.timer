(ns jp.nijohando.event.timer-test-clj
  (:require [clojure.test :as t :refer [run-tests is are deftest testing use-fixtures]]
            [clojure.core.async :as ca]
            [jp.nijohando.event.timer :as tm]
            [jp.nijohando.ext.async :as xa]
            [jp.nijohando.event :as ev]
            [jp.nijohando.deferable :as d]
            [jp.nijohando.failable :as f]))

(defn- now
  []
  (System/currentTimeMillis))

(deftest once-timer-event
  (testing "Once timer event can be got after delay"
    (d/do*
      (let [timer (tm/timer)
            _ (d/defer (ev/close! timer))
            listener (ca/chan 1)
            _ (d/defer (ca/close! listener))
            start (now)
            task-id (tm/once! timer 1000)]
        (ev/listen timer (format "/tasks/%d" task-id) listener)
        (let [x (xa/<!! listener :timeout 3000)
              time (- (now) start)]
          (f/ensure x)
          (is (nil? (:value x)))
          (is (>= time 900))
          (is (<= time 1100))))))
  (testing "Custom once timer event can be got after delay"
    (d/do*
      (let [timer (tm/timer)
            _ (d/defer (ev/close! timer))
            listener (ca/chan 1)
            _ (d/defer (ca/close! listener))
            start (now)
            task-id (tm/once! timer 1000 (ev/event "/foo/bar" :baz))]

        (ev/listen timer "/foo/bar" listener)
        (let [x (xa/<!! listener :timeout 3000)
              time (- (now) start)]
          (f/ensure x)
          (is (= "/foo/bar" (:path x)))
          (is (= :baz (:value x)))
          (is (>= time 900))
          (is (<= time 1100))))))

  (testing "Custom once timer event can be created by function"
    (d/do*
      (let [timer (tm/timer)
            _ (d/defer (ev/close! timer))
            listener (ca/chan 1)
            _ (d/defer (ca/close! listener))
            start (now)
            task-id (tm/once! timer 1000 #(ev/event "/foo/bar" :baz))]

        (ev/listen timer "/foo/bar" listener)
        (let [x (xa/<!! listener :timeout 3000)
              time (- (now) start)]
          (f/ensure x)
          (is (= "/foo/bar" (:path x)))
          (is (= :baz (:value x)))
          (is (>= time 900))
          (is (<= time 1100))))))

  (testing "Task can be cancelled"
    (d/do*
      (let [timer (tm/timer)
            _ (d/defer (ev/close! timer))
            listener (ca/chan 1)
            _ (d/defer (ca/close! listener))
            task-id (tm/once! timer 10000)]
        (ev/listen timer (format "/tasks/%d/cancelled" task-id) listener)
        (is (f/succ? (tm/cancel! timer task-id)))
        (let [x (xa/<!! listener :timeout 1000)]
          (is (f/succ? x)))))))

(deftest repeat-timer-event
  (testing "Repeated timer event can be got after init-delay"
    (d/do*
      (let [timer (tm/timer)
            _ (d/defer (ev/close! timer))
            listener (ca/chan 1)
            _ (d/defer (ca/close! listener))
            task-id (tm/repeat! timer 3000 1000)]
        (ev/listen timer (format "/tasks/%d" task-id) listener)
        (let [x (xa/<!! listener :timeout 2000)]
          (f/ensure x)
          (is (nil? (:value x)))
          (is (f/succ? (tm/cancel! timer task-id)))))))
  (testing "Custom repeated timer event can be got after init-delay"
    (d/do*
      (let [timer (tm/timer)
            _ (d/defer (ev/close! timer))
            listener (ca/chan 1)
            _ (d/defer (ca/close! listener))
            task-id (tm/repeat! timer 3000 1000 (ev/event "/foo/bar" :baz))]
        (ev/listen timer "/foo/bar" listener)
        (let [x (xa/<!! listener :timeout 2000)]
          (f/ensure x)
          (is (= "/foo/bar" (:path x)))
          (is (= :baz (:value x)))
          (is (f/succ? (tm/cancel! timer task-id)))))))
  (testing "Custom repeated timer event can be created by function"
    (d/do*
      (let [timer (tm/timer)
            _ (d/defer (ev/close! timer))
            listener (ca/chan 1)
            _ (d/defer (ca/close! listener))
            task-id (tm/repeat! timer 3000 1000 #(ev/event "/foo/bar" :baz))]
        (ev/listen timer "/foo/bar" listener)
        (let [x (xa/<!! listener :timeout 2000)]
          (f/ensure x)
          (is (= "/foo/bar" (:path x)))
          (is (= :baz (:value x)))
          (is (f/succ? (tm/cancel! timer task-id)))))))
  (testing "Repeated timer event can be got repeatedly"
    (d/do*
      (let [timer (tm/timer)
            _ (d/defer (ev/close! timer))
            listener (ca/chan 1)
            _ (d/defer (ca/close! listener))
            start (atom (now))
            task-id (tm/repeat! timer 3000 1000)]
        (ev/listen timer (format "/tasks/%d" task-id) listener)
        (let [x (xa/<!! listener :timeout 2000)
              time (- (now) @start)]
          (reset! start (now))
          (f/ensure x)
          (is (nil? (:value x)))
          (is (>= time 900))
          (is (<= time 1100)))
        (let [x (xa/<!! listener :timeout 4000)
              time (- (now) @start)]
          (reset! start (now))
          (f/ensure x)
          (is (nil? (:value x)))
          (is (>= time 2900))
          (is (<= time 3100)))
        (let [x (xa/<!! listener :timeout 4000)
              time (- (now) @start)]
          (f/ensure x)
          (is (nil? (:value x)))
          (is (>= time 2900))
          (is (<= time 3100))
          (is (f/succ? (tm/cancel! timer task-id)))))))
  (testing "Task can be cancelled"
    (d/do*
      (let [timer (tm/timer)
            _ (d/defer (ev/close! timer))
            listener (ca/chan 1)
            _ (d/defer (ca/close! listener))
            task-id (tm/repeat! timer 10000 10000)]
        (ev/listen timer (format "/tasks/%d/cancelled" task-id) listener)
        (is (f/succ? (tm/cancel! timer task-id)))
        (let [x (xa/<!! listener :timeout 1000)]
          (is (f/succ? x)))))))

(deftest cancel-all-tasks
  (testing "All Tasks can be cancelled at once"
    (d/do*
      (let [timer (tm/timer)
            _ (d/defer (ev/close! timer))
            listener (ca/chan 1)
            _ (d/defer (ca/close! listener))
            task-id1 (tm/once! timer 10000)
            task-id2 (tm/once! timer 10000)]
        (tm/cancel-all! timer)
        (is (= :task-not-found @(tm/cancel! timer task-id1)))
        (is (= :task-not-found @(tm/cancel! timer task-id2)))))))
