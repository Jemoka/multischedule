(ns multischedule.hosts.simple
  (:require [multischedule
             [node :as node]]
            [clojure.core.async :as async :refer [>! >!! <! <!! go]]))

;; https://stackoverflow.com/questions/3249334/test-whether-a-list-contains-a-specific-value-in-clojure
(defn in? 
  "true if coll contains elm"
  [coll elm]  
  (some #(= elm %) coll))

(defn process-backlog
  "process backlog for a node. Expect Backlog to be sorted reverse by load"

  ;; returns processed nodes and unprocessed nodes

  ;; we are relaxing the 1/0 knapsack problem to fractional
  ;; even if we arn't actually doing fractional knapsack.
  ;; this is because overcommiting is allowed

  [self backlog]

  (let [processed (reduce (fn [history curr]
                            ;; we first calculate the new load
                            (let [new-load (+ (first history) (:load curr))]
                              ;; then, if our current load is
                              ;; over, we will move on to the
                              ;; next item without conjoining.
                              ;;
                              ;; we elect to take largest item
                              ;; first.
                              (if (< new-load (:max-load self))
                                (cons new-load
                                      (cons curr (rest history)))
                                history)))
                          '(0)
                          backlog)]

    ;; getting both the processed result in a list
    ;; and unprocessed backlog in a list
    (list (rest processed)
          (filter #(not (in? processed %)) backlog))))

(defn process
  "process function by running through 1 it. of the algorithum"

  [self state node task]

  ;; Append the current task to the backlog 
  ;; get all the tasks in the backlog
  (let [tasks (if (-> task nil? not)
                (conj (:backlog self) task)
                (:backlog self))
        ;; sort the backlog by load and filter
        ;; by those under the limit
        backlog (filter #(< (:load %) ;; check for load limit
                            (- (:max-load self)
                               (:load self))) 
                        (reverse
                         (sort-by #(:load %) tasks)))]
    ;; Take enough of the tasks such that it will fill
    ;; to just undercommit
    (let [[processed pending] (process-backlog self backlog)]
      ;; finally, we "process" the tasks in the load sequentially and
      ;; return the results of processing.
      (do-run processed))))

(defn do-run
  "actually run a bunch of tasks on parallel threads"
  [tasks]

;; we first nonblocking put everything
;; on the communication channel.
  (let [channel (async/to-chan tasks)]

    ;; we will then, on different threads
    ;; perform the operation and get results
    ;; for the number of task times
    ;; we will use a async merge operation
    ;; to merge all together; then, we will
    ;; pop results off via block takes.
    (let [results (async/merge
                   (repeatedly
                           (count tasks)
                           ;; go take one off...
                           #(go (let [task (<! channel)]
                                 ;; and do it!

                                 ;; print debug message as we are in
                                 ;; demonstration
                                 ;; (println "DEBUG doing fn" (:fn task))
                                 ;; and do the task
                                 {(:name task) (apply (:fn task) (:args task))}))))]
      ;; block take for results
      (reduce into (repeatedly (count tasks) #(<!! results))))))

