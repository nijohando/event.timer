(ns jp.nijohando.event.timer-test-cljs
  (:require [clojure.test :as t :refer-macros [is deftest testing async]]
            [clojure.core.async :as ca :include-macros true]
            [jp.nijohando.event.timer :as tm]
            [jp.nijohando.ext.async :as xa :include-macros true]
            [jp.nijohando.event :as ev :include-macros true]
            [jp.nijohando.deferable :as d :include-macros true]
            [jp.nijohando.failable :as f :include-macros true]))

(defn- now
  []
  (.now js/Date))

(deftest once-timer-event
  (testing "Once timer event can be got after delay"
    (async end
      (d/do** done
        (let [timer (tm/timer)
              _ (d/defer (ev/close! timer))
              listener (ca/chan 1)
              _ (d/defer (ca/close! listener))
              start (now)
              task-id (tm/once! timer 1000)]
          (ev/listen timer (str "/tasks/" task-id) listener)
          (ca/go
            (let [x (xa/<! listener :timeout 3000)
                  time (- (now) start)]
              (f/ensure x)
              (is (nil? (:value x)))
              (is (and (> time 1000) (< time 1200)))
              (done)
              (end))))))))

(deftest custom-once-timer-event
  (testing "Custom Once timer event can be got after delay"
    (async end
      (d/do** done
        (let [timer (tm/timer)
              _ (d/defer (ev/close! timer))
              listener (ca/chan 1)
              _ (d/defer (ca/close! listener))
              start (now)
              task-id (tm/once! timer 1000 (ev/event "/foo/bar" :baz))]
          (ev/listen timer "/foo/bar" listener)
          (ca/go
            (let [x (xa/<! listener :timeout 3000)
                  time (- (now) start)]
              (f/ensure x)
              (is (= "/foo/bar" (:path x)))
              (is (= :baz (:value x)))
              (is (and (> time 1000) (< time 1200)))
              (done)
              (end))))))))

(deftest custom-once-timer-event-by-function
  (testing "Once timer event can be created by function"
    (async end
      (d/do** done
        (let [timer (tm/timer)
              _ (d/defer (ev/close! timer))
              listener (ca/chan 1)
              _ (d/defer (ca/close! listener))
              start (now)
              task-id (tm/once! timer 1000 #(ev/event "/foo/bar" :baz))]
          (ev/listen timer "/foo/bar" listener)
          (ca/go
            (let [x (xa/<! listener :timeout 3000)
                  time (- (now) start)]
              (f/ensure x)
              (is (= "/foo/bar" (:path x)))
              (is (= :baz (:value x)))
              (is (and (> time 1000) (< time 1200)))
              (done)
              (end))))))))

(deftest once-timer-task-cancel
  (testing "Once timer event can be cancelled"
    (async end
      (d/do** done
        (let [timer (tm/timer)
              _ (d/defer (ev/close! timer))
              listener (ca/chan 1)
              _ (d/defer (ca/close! listener))
              task-id (tm/once! timer 10000)]
          (ev/listen timer (str "/tasks/" task-id "/cancelled") listener)
          (is (f/succ? (tm/cancel! timer task-id)))
          (ca/go
            (let [x (xa/<! listener :timeout 1000)]
              (is (f/succ? x))
              (done)
              (end))))))))

(deftest repeated-timer-event-init-delay
  (testing "Repeated timer event can be got after init-delay"
    (async end
      (d/do** done
        (let [timer (tm/timer)
              _ (d/defer (ev/close! timer))
              listener (ca/chan 1)
              _ (d/defer (ca/close! listener))
              task-id (tm/repeat! timer 3000 1000)]
          (ev/listen timer (str "/tasks/" task-id) listener)
          (ca/go
            (let [x (xa/<! listener :timeout 2000)]
              (f/ensure x)
              (is (nil? (:value x)))
              (is (f/succ? (tm/cancel! timer task-id))))
            (done)
            (end)))))))

(deftest custom-repeated-timer-event-init-delay
  (testing "Custom repeated timer event can be got after init-delay"
    (async end
      (d/do** done
        (let [timer (tm/timer)
              _ (d/defer (ev/close! timer))
              listener (ca/chan 1)
              _ (d/defer (ca/close! listener))
              task-id (tm/repeat! timer 3000 1000 (ev/event "/foo/bar" :baz))]
          (ev/listen timer "/foo/bar" listener)
          (ca/go
            (let [x (xa/<! listener :timeout 2000)]
              (f/ensure x)
              (is (= "/foo/bar" (:path x)))
              (is (= :baz (:value x)))
              (is (f/succ? (tm/cancel! timer task-id))))
            (done)
            (end)))))))

(deftest custom-repeated-timer-event-by-function
  (testing "Custom repeated timer event can be created by function"
    (async end
      (d/do** done
        (let [timer (tm/timer)
              _ (d/defer (ev/close! timer))
              listener (ca/chan 1)
              _ (d/defer (ca/close! listener))
              task-id (tm/repeat! timer 3000 1000 #(ev/event "/foo/bar" :baz))]
          (ev/listen timer "/foo/bar" listener)
          (ca/go
            (let [x (xa/<! listener :timeout 2000)]
              (f/ensure x)
              (is (= "/foo/bar" (:path x)))
              (is (= :baz (:value x)))
              (is (f/succ? (tm/cancel! timer task-id))))
            (done)
            (end)))))))

(deftest repeated-timer-event-repeat
  (testing "Repeated timer event can be got repeatedly"
    (async end
      (d/do** done
       (let [timer (tm/timer)
             _ (d/defer (ev/close! timer))
             listener (ca/chan 1)
             _ (d/defer (ca/close! listener))
             start (atom (now))
             task-id (tm/repeat! timer 3000 1000)]
         (ev/listen timer (str "/tasks/" task-id) listener)
         (ca/go
           (let [x (xa/<! listener :timeout 2000)
                 time (- (now) @start)]
             (reset! start (now))
             (f/ensure x)
             (is (nil? (:value x)))
             (is (and (> time 1000) (< time 1200))))
           (let [x (xa/<! listener :timeout 4000)
                 time (- (now) @start)]
             (reset! start (now))
             (f/ensure x)
             (is (nil? (:value x)))
             (is (and (> time 3000) (< time 3200))))
           (let [x (xa/<! listener :timeout 4000)
                 time (- (now) @start)]
             (f/ensure x)
             (is (nil? (:value x)))
             (is (and (> time 3000) (< time 3200)))
             (is (f/succ? (tm/cancel! timer task-id))))
           (done)
           (end)))))))

(deftest repeated-timer-task-cancell
  (testing "Repeated timer event can be cancelled"
    (async end
      (d/do** done
        (let [timer (tm/timer)
              _ (d/defer (ev/close! timer))
              listener (ca/chan 1)
              _ (d/defer (ca/close! listener))
              task-id (tm/repeat! timer 3000 1000)]
          (ev/listen timer (str "/tasks/" task-id "/cancelled") listener)
          (is (f/succ? (tm/cancel! timer task-id)))
          (ca/go
            (let [x (xa/<! listener :timeout 1000)]
              (is (f/succ? x))
              (done)
              (end))))))))

(deftest cancel-all-tasks
  (testing "All Tasks can be cancelled at once"
    (d/do*
      (let [t (tm/timer)
            _ (d/defer (ev/close! t))
            listener (ca/chan 1)
            _ (d/defer (ca/close! listener))
            task-id1 (tm/once! t 10000)
            task-id2 (tm/once! t 10000)]
        (tm/cancel-all! t)
        (is (= :task-not-found @(tm/cancel! t task-id1)))
        (is (= :task-not-found @(tm/cancel! t task-id2)))))))
