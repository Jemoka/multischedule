(ns multischedule.task)

(defn create-task
  "creates a task"

  [name load f & args]

  {:name name
   :fn f
   :args args
   :load load})
