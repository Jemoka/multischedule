(ns multischedule.node
  (:require [clojure.core.async :as async :refer [>! >!! <! <!! go]]))

;;;; Utilities ;;;;
(defn update-node
  "update load for a node"

  [state node-name & res]

  (assoc state :nodes (conj (filter #(not (= (:name %) node-name)) (:nodes state))
                            (apply
                             (partial assoc (first (filter #(= (:name %) node-name) (:nodes state))))
                             res))))

;;;; simple handlers ;;;;
;; https://stackoverflow.com/questions/3249334/test-whether-a-list-contains-a-specific-value-in-clojure
(defn in? 
  "true if coll contains elm"
  [coll elm]  
  (some #(= elm %) coll))

;; hoisting
(declare process-simple)

;;;; Process a task ;;;;
(defn process
  "Use the node's processor to process the task"

  ([state node]
   (process state node nil))
  ([state node task]


   (let [node (first (filter
                      #(= (:name %) node)
                      (:nodes state)))]
     
     (case (:type
            (first (filter
                    #(= (:name %)
                        (:host node))
                    (:hosts state))))
       'simple (process-simple node state node task)))))

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
                              (if (< new-load (- (:max-load self) (:load self)))
                                (cons new-load
                                      (cons curr (rest history)))
                                history)))
                          '(0)
                          backlog)]

    

      
    ;; getting both the processed result in a list
    ;; and unprocessed backlog in a list
    (list (rest processed)
          (filter #(not (in? processed %)) backlog)
          (first processed))))

(defn do-run
  "actually run a bunch of tasks on parallel threads"
  [new-state self tasks backlog]

  ;; we first nonblocking put everything
  ;; on the communication channel.
  (let [channel (async/to-chan tasks)
        backlog (async/to-chan backlog)
        results (async/chan)] ;; to capture any upcycled tasks


    ;; we will then, on different threads
    ;; perform the operation and get results
    ;; for the number of task times
    ;; we will use a async merge operation
    ;; to merge all together; then, we will
    ;; pop results off via block takes.
    (let [mix (async/mix results)
          default (async/merge
                   (repeatedly
                    (count tasks)
                    ;; go take one off...
                    (fn [] (go (let [task (<! channel)]
                                 ;; and do it!
                                 ;; once we are done, ask for more things to process
                                 ;; and add those to the results as well.
                                 ;;
                                 ;; this is the "reactive" part of the algorithum
                                 ;; where by we will find the *lightest* node to
                                 ;; use to process

                                 ;; ask the most available node to process the backlog
                                 (go (let [next-node (reverse (sort-by #(- (:max-load %)
                                                                           (:load %)) (:nodes new-state)))]
                                       (if-let [next-task (<! backlog)]
                                         (async/admix mix (process new-state (:name (first next-node)) next-task))
                                         )))
                                 {(:name task) (apply (:fn task) (:args task))
                                  :worker (:name self)})))))]

      ;; Dump the rest of backlog away
      (let [next-node (reverse (sort-by #(- (:max-load %)
                                            (:load %)) (:nodes new-state)))]
        (when-let [next-task (<!! backlog)]
          (async/admix mix (process new-state (:name (first next-node)) next-task))))
      
      ;; add defaults channel
      (async/admix mix default)
      ;; block take for results
      results)))

(defn process-simple
  "process function by running through 1 it. of the algorithum"

  [self state node task]

  

  ;; Append the current task to the backlog 
  ;; get all the tasks in the backlog
  (let [tasks (if (and (-> task nil? not)
                       (not (in? (:backlog self) task)))
                (conj (:backlog self) task)
                (:backlog self))
        ;; sort the backlog by load and filter
        ;; by those under the limit
        backlog (reverse
                 (sort-by #(:load %) tasks))]

    

    ;; Take enough of the tasks such that it will fill
    ;; to just undercommit. This is the "proactive"
    ;; part of the algorithum taking only what it can process
    (let [[processed pending load] (process-backlog self backlog)]
      ;; we "process" the tasks in the load sequentially and
      ;; return the results of processing.
      ;;
      ;; we additionally update the capacity of the load
      
      ;; (if (= (:name self) 'node-two)
      ;;   (println (:name self)))

      (do-run (update-node state (:name self)
                           :load load
                           :backlog pending)
              self processed pending))))


;;;; Node Manipulation Utilities ;;;;
(defn add-node
  "add a node to the current state"

  [state name host max-load]

  (assoc state :nodes
         (conj (:nodes state)
               {:name name
                :load 0
                :max-load max-load
                :backlog []
                :host host})))

(defn remove-node
  "delete a node from the current state"

  [state name]

  (assoc state :nodes
         (filter
          #(not (= (:name %) name))
          (:nodes state))))
