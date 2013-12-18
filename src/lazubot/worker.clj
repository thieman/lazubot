(ns lazubot.worker
  (:require [clojail.core :refer [sandbox]]
            [clojure.core.async :refer [>!! <!! chan sliding-buffer]]
            [com.keminglabs.zmq-async.core :refer [register-socket!]]))

(def sb (sandbox [])) ;; sandbox without any testers, only using timeout capability

(defn -main []
  (let [addr "tcp://eth0:8080"
        [reply-in reply-out] (repeatedly 2 #(chan (sliding-buffer 64)))]
    (register-socket! {:in reply-in :out reply-out :socket-type :rep
                       :configurator (fn [socket] (.bind socket addr))})
    (println "Worker initialized")
    (when-let [message (String. (<!! reply-out))]
      (>!! reply-in message)
      (recur))))
