# event.timer

[![Clojars Project](https://img.shields.io/clojars/v/jp.nijohando/event.timer.svg)](https://clojars.org/jp.nijohando/event.timer)
[![CircleCI](https://circleci.com/gh/nijohando/event.timer.svg?style=shield)](https://circleci.com/gh/nijohando/event.timer)

Experimental simple timer integrated with [nijohando/event](https://github.com/nijohando/event) bus.

## Installation

#### Ligningen / Boot

```clojure
[jp.nijohando/event.timer "0.1.0-SNAPSHOT"]
```

#### Clojure CLI / deps.edn

```clojure
jp.nijohando/event.timer {:mvn/version "0.1.0-SNAPSHOT"}
```

## Usage

```clojure
(require '[jp.nijohando.event :as ev]
         '[jp.nijohando.event.timer :as tm]
         '[clojure.core.async :as ca])
```

### Create a timer bus

Function `timer` creates an event bus that acts as a timer.

```clojure
(def bus (tm/timer))
```

### Once timer event

Function `once!` starts a timer task that emits a timer event only once after the delay.  
The function returns the id of the generated task which is part of an event path like `/tasks/:id`

```clojure
(let [delay 3000 
      task-id (tm/once! bus delay)
      listener (ca/chan 1)]
  (ev/listen bus (str "/tasks/" task-id) listener)
  (ca/go
    (when-some [event (ca/<! listener)]
      ;; Once timer event can be got after 3 seconds 
      (prn "Emitted!")
      (ca/close! listener)))
  task-id)
;=> 1
;   Emitted!
```

### Repeated timer event

Function `repeat!` starts a timer task after init delay and emits a timer event repeatedly.  
The function returns the id of the generated task which is part of an event path like `/tasks/:id`

```clojure
(let [init-delay 1000 
      period 3000
      task-id (tm/repeat! bus period init-delay)
      listener (ca/chan 1)]
  (ev/listen bus (str "/tasks/" task-id) listener)
  (ca/go-loop []
    (when-some [event (ca/<! listener)]
      ;; Repeated timer event can be got every 3 seconds 
      (prn "Emitted!")
      (recur)))
  task-id)
;=> 2
;   Emitted!
;   Emitted!
;   ...
```

### Task cancellation

Timer tasks can be cancelled by function `cancel!`  
Event `/tasks/:id/cancelled` occurs when the task is cancelled.

```clojure
(let [delay 3000 
      task-id (tm/once! bus delay)
      listener (ca/chan 1)]
  (ev/listen bus (str "/tasks/" task-id "/cancelled") listener)
  (ca/go
    (when-some [event (ca/<! listener)]
      (prn "Cancelled!")
      (ca/close! listener)))
  (tm/cancel! bus task-id))
;=> Cancelled!
```

Function `cancel-all!` cancels all tasks at once.

```clojure
(tm/cancel-all! bus)
```


### Custom timer event

Function `once!` `repeat!` can also emit custom timer event.  
A custom timer event or a function which creates custom timer event can be applied at last argument.

```clojure
(tm/once! bus delay (ev/event "/foo/bar" :baz))
(tm/once! bus delay #(ev/event "/foo/bar" :baz))
(tm/repeat! bus period init-delay (ev/event "/foo/bar" :baz))
(tm/repeat! bus period init-delay #(ev/event "/foo/bar" :baz))
```

## License

© 2018 nijohando  

Distributed under the Eclipse Public License either version 1.0 or (at your option) any later version.

