(ns lazubot.worker
  (:require [clojail.core :refer [sandbox]]
            [clojure.core.async :refer [>!! <!! chan sliding-buffer]]
            [com.keminglabs.zmq-async.core :refer [register-socket!]]
            [cheshire.core :as cheshire]))

(def liberal-sandbox (sandbox []))

(defn eval-form
  "Take in a Clojure form string. Return a string of the evaluation
  result."
  [form-string]
  (str (liberal-sandbox (read-string form-string))))

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
          (>!! reply-in (eval-form message))
          (catch Exception e
            (>!! reply-in (str e))))
        (recur)))))
