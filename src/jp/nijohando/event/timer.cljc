(ns jp.nijohando.event.timer
  (:refer-clojure :exclude [repeat])
  (:require [jp.nijohando.event :as ev]
            [jp.nijohando.event.protocols :as evp]
            [jp.nijohando.failable :as f]
            [jp.nijohando.deferable :as d :include-macros true]
            [clojure.core.async :as ca :include-macros true]))

(defprotocol Timer
  (-once! [this delay value])
  (-repeat! [this period init-delay value])
  (-cancel! [this task-id])
  (-cancel-all! [this]))

(defn once!
  ([timer delay]
   (once! timer delay nil))
  ([timer delay event]
   (-once! timer delay event)))

(defn repeat!
  ([timer period init-delay]
   (repeat! timer period init-delay nil))
  ([timer period init-delay value]
   (-repeat! timer period init-delay value)))

(defn cancel!
  [timer task-id]
  (-cancel! timer task-id))

(defn cancel-all!
  [timer]
  (-cancel-all! timer))

(defn timer
  ([]
   (timer nil))
  ([{:keys [buffer-size]
     :or {buffer-size 32} :as opts}]
   (let [bus (ev/sliding-bus buffer-size)
         emitter (ca/chan)
         idseq (atom 0)
         tasks (atom {})
         next-id! #(swap! idseq inc)
         register-task (fn [task-id cancel-fn] (swap! tasks assoc task-id cancel-fn))]
     (ev/emitize bus emitter)
     (reify
       Timer
       (-once! [this delay event]
         (d/do** done
           (let [cancel-ch (ca/chan)
                 task-id (next-id!)]
             (ca/go
               (let [x (ca/alt! [(ca/timeout delay)] ([_] :fire)
                                [cancel-ch]          ([_] :cancel))]
                 (when (and event (= :fire x))
                   (->> (if (fn? event)
                          (event)
                          event)
                        (ca/>! emitter)))
                 (->> (condp = x
                        :fire   (str "/tasks/" task-id)
                        :cancel (str "/tasks/" task-id "/cancelled"))
                      ev/event
                      (ca/>! emitter))
                 (done)))
             (register-task task-id #(ca/close! cancel-ch))
             task-id)))
       (-repeat! [this period init-delay event]
         (d/do** done
           (let [cancel-ch (ca/chan)
                 task-id (next-id!)]
             (ca/go-loop [first? true]
               (let [ms (if first? init-delay period)
                     x (ca/alt! [(ca/timeout ms)] ([_] :fire)
                                [cancel-ch]       ([_] :cancel))
                     path (condp = x
                            :fire   (str "/tasks/" task-id)
                            :cancel (str "/tasks/" task-id "/cancelled"))]
                 (when (and event (= :fire x))
                   (->> (if (fn? event)
                          (event)
                          event)
                        (ca/>! emitter)))
                 (ca/>! emitter (ev/event path))
                 (if (= :cancel x)
                   (done)
                   (recur false))))
             (register-task task-id #(ca/close! cancel-ch))
             task-id)))
       (-cancel! [this task-id]
         (if-some [cancel-fn (get @tasks task-id )]
           (do
             (cancel-fn)
             (swap! tasks dissoc task-id)
             nil)
           (f/fail :task-not-found)))
       (-cancel-all! [this]
         (doseq [[_ cancel-fn] @tasks]
           (cancel-fn))
         (reset! tasks {}))
       evp/Emittable
       (emitize [_ emitter-ch reply-ch]
         (ev/emitize bus emitter-ch reply-ch))
       evp/Listenable
       (listen [_ routes listener-ch]
         (ev/listen bus routes listener-ch))
       evp/Closable
       (close! [this]
         (-cancel-all! this)
         (ca/close! emitter)
         (ev/close! bus))))))
