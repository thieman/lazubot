(ns lazubot.docker
  (:require [clojure.string :as string]
            [clojure.java.io :as io]
            [clojure.java.shell :as shell]
            [clojure.core.async :refer [go chan sliding-buffer <! >!]]
            [com.keminglabs.zmq-async.core :refer [register-socket!]]
            [clj-http.client :as client]
            [cheshire.core :as cheshire])
  (:import [java.io PushbackReader]))

(def config (with-open [r (io/reader (io/resource "private/config"))]
              (read (PushbackReader. r))))

(def workers (ref {}))

(defn docker-uri [& rest]
  (apply str (:docker-url config) rest))

(defn run-new-container!
  "Build the worker image if necessary, start a new worker container,
  and return its ID."
  []
  (shell/sh "docker" "build" "-no-cache=true" "-t=lazubot-worker" "resources/public")
  (-> (shell/sh "docker" "run" "-d=true" "-expose=8080" "lazubot-worker")
      (:out)
      (string/trim-newline)))

(defn container-address
  "Return the IP address of a container by its ID."
  [container-id]
  (get-in (client/get (docker-uri "/containers/" container-id "/json")
                      {:as :json})
          [:body :NetworkSettings :IpAddress]))

(defn register-worker!
  "Add a worker doc to the workers ref."
  [worker-doc]
  (dosync
   (commute workers assoc (:id worker-doc) worker-doc)))

(defn eval-on-worker
  "Send a Clojure form to a worker for evaluation. Return a channel
  onto which the result will be posted."
  [form]
  (let [workers @workers
        worker-doc (get workers (first (keys workers)))
        result-channel (chan)]
    (go (>! (:in worker-doc) form)
        (>! result-channel (String. (<! (:out worker-doc)))))))

(defn add-worker! []
  (let [container-id (run-new-container!)
        container-address (container-address container-id)
        [request-in request-out] (repeatedly 2 #(chan (sliding-buffer 64)))
        worker-doc {:id container-id :in request-in :out request-out}]
    (register-socket! {:in request-in :out request-out :socket-type :req
                       :configurator (fn [socket] (.connect socket container-address))})
    (register-worker! worker-doc)))
