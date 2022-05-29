(ns multischedule.node
  (:require [multischedule.hosts
             [simple :as simple]]))

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
       'simple (simple/process node state node task)))))

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
