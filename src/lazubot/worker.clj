(ns lazubot.worker
  (:require [clojure.core.async :refer [>!! <!! chan sliding-buffer]]
            [com.keminglabs.zmq-async.core :refer [register-socket!]]
            [cheshire.core :as cheshire]))

(defn eval-form
  "Take in a Clojure form string. Return a string of the evaluation
  result."
  [form-string]
  (str (eval (read-string form-string))))

(defn -main []
  (let [addr "tcp://eth0:8080"
        [reply-in reply-out] (repeatedly 2 #(chan (sliding-buffer 64)))]
    (register-socket! {:in reply-in :out reply-out :socket-type :rep
                       :configurator (fn [socket] (.bind socket addr))})
    (println "Worker initialized")
    (loop []
      (when-let [message (String. (<!! reply-out))]
        (println (str "Received " message))
        (try
          (let [evaluated (eval-form message)]
            (println (str "Sending " evaluated))
            (>!! reply-in evaluated))
          (catch Exception e
            (>!! reply-in (str e))))
        (recur)))))
