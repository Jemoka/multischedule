;; multischedule.core
;; core and runner facilities for multiprocessing

(ns multischedule.core
  (:require [clojure.core.async :as async :refer [>! >!! <! <!! go]]
            [multischedule
             [node :as node]
             [host :as host]
             [task :as task]])
  (:gen-class))

"""
multischedule is a demonstration-only load-sharing facility created in Clojure.
We will attempt to demonstrate some of the features of multischedule.
"""

(defn -main
  "I don't do a whole lot ... yet."
  [& args]

  (println "Hello!")

  ;;;; Tasks ;;;;

  ;; Under this scheme, =tasks= are defined as any unit of work
  ;; which has some weight. For us, a task looks something like
  ;; this:

  ;; (multischedule.task/create-task name load-val func args)
  (task/create-task :test 100 #(identity 1))

  ;; Because we are implementing generic load-sharing, we actually
  ;; don't care what the task /is/. Therefore, for the actual 'work'
  ;; of the task, we will just include it as #(identity n) "just return"
  ;; for the purposes of demonstration.
  ;;
  ;; Instead, we will play with load values to simulate heavier/lighter
  ;; tasks.

  ;; Here's a random task list!
  (def ^:dynamic *task-list* [(task/create-task :one 50 #(identity 1))
                              (task/create-task :two 70 #(identity 2))
                              (task/create-task :three 70 #(identity 3))
                              (task/create-task :four 10 #(identity 4))
                              (task/create-task :five 5 #(identity 5))])

  ;; A word about the task list: you can see the "name" of the task
  ;; (keyword :num) corresponds to the value it returns.


  ;;;; States ;;;;

  ;; To seed a global state, we can hand the entire task list to one
  ;; of the nodes handling processing. Remember, we are implementing
  ;; an load-sharing mechanism, which means that every node is expected
  ;; to act as a "load-balancer" and /share/ its work with others when
  ;; needed. We will hand it to node-one here.

  (def ^:dynamic *global-state*
    {:name "network-name"
     :time (System/currentTimeMillis)
     :hosts [{:name 'host-one
              :type 'simple}]
     :nodes [{:name 'node-one :load 0 :max-load  120 :backlog *task-list* :host 'host-one}
             {:name 'node-two :load 0 :max-load 50 :backlog [] :host 'host-one}
             {:name 'node-three :load 0 :max-load 85 :backlog [] :host 'host-one}]})

  ;; A quick word about "hosts". The codebase is set up such that the
  ;; load-sharing mechanism is actually generic. Setting the host of
  ;; nodes as "simple" simply means that we are using the symmetic
  ;; load-sharing mechanism implemented in =nodes.clj=. 

  ;;;; Running! ;;;;

  ;; multisimplify currently only performs one-step simulation. That
  ;; is: it will achieve as much as it can, performs one-shot load
  ;; sharing, given "one step" in time. It will share any loads as in
  ;; needs, and process the results.
  ;;
  ;; Future work can bring this up to a functional online network.

  ;; We will poll for node-one. This asks to process the backlog in
  ;; node one.
  (def ^:dynamic *res-stream* (node/process *global-state* 'node-one))

  ;; The return type from *res-stream* is a Java many-to-many task
  ;; stream. We can use blocking =take=s (<!!) to block the current
  ;; thread and wait for results.

  ;; We will attempt te get all 5 results
  (def ^:dynamic *results* (repeatedly 5 #(<!! *res-stream*)))

  (println *results*) ; ({:five 5, :worker node-one}
                      ;  {:two 2, :worker node-three}
                      ;  {:four 4, :worker node-one}
                      ;  {:one 1, :worker node-three}
                      ;  {:three 3, :worker node-one})
                             
  ;; As you can see, the load sharing mechanism correctly
  ;; recognized that one and three are the most free
  ;; and shared the load appropriately.
  ;;
  ;; This is done with relaxing a 1/0 knapsack. See the README
  ;; on more of how this was implemented.
  )

