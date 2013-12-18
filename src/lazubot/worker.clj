(ns lazubot.worker
  (:require [clojail.core :refer [sandbox]]
            [clojure.core.async :refer [>!! <!! chan sliding-buffer]]
            [com.keminglabs.zmq-async.core :refer [register-socket!]]
            [cheshire.core :as cheshire]))

(def liberal-sandbox (sandbox []))

(defn eval-forms
  "Take in a JSON string of Clojure forms. Return a JSON string of
  their evaluation docs."
  [forms-json]
  (let [forms (cheshire/parse-string forms-json)]
    (cheshire/generate-string (map #(liberal-sandbox (read-string %)) forms))))

(defn -main []
  (let [addr "tcp://eth0:8080"
        [reply-in reply-out] (repeatedly 2 #(chan (sliding-buffer 64)))]
    (register-socket! {:in reply-in :out reply-out :socket-type :rep
                       :configurator (fn [socket] (.bind socket addr))})
    (println "Worker initialized")
    (loop []
      (when-let [message (String. (<!! reply-out))]
        (try
          (>!! reply-in (eval-forms message))
          (catch Exception e
            (>!! reply-in (str e))))
        (recur)))))
