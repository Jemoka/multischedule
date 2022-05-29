(ns multischedule.host)

;;;; Host Manipulation Utilities ;;;;
(defn add-dummy-host
  "add a dummy hosts to the current state"

  [state name]

  (assoc state :hosts
         (conj (:hosts state)
               {:name name
                :type 'demo})))

(defn remove-dummy-host
  "delete a dummy host from the current state"

  [state name]

  (assoc state :hosts
         (filter
          #(not (= (:name %) name))
          (:hosts state))))





