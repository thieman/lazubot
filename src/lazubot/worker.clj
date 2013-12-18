(ns lazubot.worker
  (:require [clojail.core :refer [sandbox]]
            [clojure.core.async :refer [>! <! go go-loop chan sliding-buffer]]
            [com.keminglabs.zmq-async.core :refer [register-socket!]]))

(def sb (sandbox [])) ;; sandbox without any testers, only using timeout capability

(defn -main []
  (let [addr (get (System/getenv) "MASTER_PORT")
        [reply-in reply-out] (repeatedly 2 #(chan (sliding-buffer 10)))]
    (register-socket! {:in reply-in :out reply-out :socket-type :rep
                       :configurator (fn [socket] (.connect socket addr))})
    (go-loop []
             (when-let [message (<! reply-in)]
               (>! reply-out message)
               (recur)))
    (println "Worker initialized")))
