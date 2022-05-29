(ns multischedule.core
  (:require [clojure.core.async :as async :refer [>! >!! <! <!! go]]
            [multischedule
             [node :as node]
             [host :as host]
             [task :as task]])
  (:gen-class))

(def *global-state*
  {:name "network-name"
   :time (System/currentTimeMillis)
   :hosts [{:name 'host-one
            :type 'simple}]
   :nodes [{:name 'node-one :load 0 :max-load 190 :backlog [(task/create-task :one 50 #(identity 1))
                                                            (task/create-task :two 70 #(identity 2))
                                                            (task/create-task :three 70 #(identity 3))
                                                            (task/create-task :four 10 #(identity 4))
                                                            (task/create-task :five 5 #(identity 5))] :host 'host-one}
           {:name 'node-two :load 0 :max-load 1000 :backlog [] :host 'host-one}]})

;; https://personal.utdallas.edu/~ravip/cs6378/slides/node5.html
(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (println "Hello, World!"))

(node/process *global-state* 'node-one)

(let [a (node/process *global-state* 'node-one)]
  (repeatedly 2 #(<!! a)))
(node/update-node *global-state* 'node-one :load 10)
(println "")

(map #(println %) '(1 3 4))
(not (nil? 1))



(cons 3 '(1))

