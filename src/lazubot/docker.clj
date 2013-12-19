(ns lazubot.docker
  (:require [clojure.string :as string]
            [clojure.java.io :as io]
            [clojure.tools.logging :refer [debug info warn]]
            [clojure.java.shell :as shell]
            [clojure.core.async :refer [go go-loop chan sliding-buffer <! >! timeout alts!]]
            [com.keminglabs.zmq-async.core :refer [register-socket!]]
            [clj-http.client :as client]
            [cheshire.core :as cheshire])
  (:import [java.io PushbackReader]))

(def config (with-open [r (io/reader (io/resource "private/config"))]
              (read (PushbackReader. r))))

(def workers (ref {}))

(defn docker-uri [& rest]
  (apply str (:docker-url config) rest))

(defn build-container!
  "Build the worker image. Should only need to do this once unless you
  want to do a fresh pull of the repository."
  []
  (info "Building lazubot-worker container")
  (shell/sh "docker" "build" "-no-cache=true" "-t=lazubot-worker" "resources/public"))

(defn run-new-container!
  "Start a new worker container and return its ID."
  []
  (let [container-id (-> (shell/sh "docker" "run" "-d=true" "-expose=8080" "lazubot-worker")
                         (:out)
                         (string/trim-newline))]
    (info "Started container " container-id)
    container-id))

(defn container-address
  "Return the IP address of a container by its ID."
  [container-id]
  (get-in (client/get (docker-uri "containers/" container-id "/json")
                               {:as :json})
          [:body :NetworkSettings :IPAddress]))

(defn lock-worker!
  "Lock a worker in the workers ref."
  [worker-doc]
  (dosync
   (commute workers assoc-in [(:id worker-doc) :locked] true)))

(defn unlock-worker!
  "Unlock a worker in the workers ref."
  [worker-doc]
  (dosync
   (commute workers assoc-in [(:id worker-doc) :locked] false)))

(defn register-worker!
  "Add a worker doc to the workers ref."
  [worker-doc]
  (dosync
   (commute workers assoc (:id worker-doc) worker-doc)))

(defn add-worker! []
  "Start a new lazubot-worker container and add its worker-doc to the
  workers ref."
  (let [container-id (run-new-container!)
        container-address (container-address container-id)
        socket-address (str "tcp://" container-address ":" (:expose-port config))
        [request-in request-out] (repeatedly 2 #(chan (sliding-buffer 64)))
        worker-doc {:id container-id :in request-in :out request-out
                    :address socket-address :locked false}]
    (register-socket! {:in request-in :out request-out :socket-type :req
                       :configurator (fn [socket] (.connect socket socket-address))})
    (register-worker! worker-doc)))

(defn replace-worker!
  "Remove the given worker from the workers ref, kill and remove it in
  Docker, and spin up a new one."
  [worker-doc]
  (dosync
   (commute workers dissoc (:id worker-doc)))
  (add-worker!))

(defn acquire-worker-doc!
  "Get an unlocked worker doc, lock it, and return it. If no worker
  docs are unlocked, return nil."
  []
  (when-let [worker-doc (->> (seq @workers)
                             (map second)
                             (remove :locked)
                             (first))]
    (dosync
     (commute workers assoc-in [(:id worker-doc) :locked] true))
    worker-doc))

(defn eval-on-worker
  "Send a Clojure form string to a worker for evaluation.  Return a
  channel onto which the result will be posted."
  [form]
  (go-loop [form form]
   (let [worker-doc (acquire-worker-doc!)]
     (if-not worker-doc
       (do (<! (timeout 500))
           (recur form))
       (let [result-channel (chan)]
         (>! (:in worker-doc) form)
         (let [[response channel] (alts! [(:out worker-doc) (timeout 10000)])]
           (if (= channel (:out worker-doc))
             (>! result-channel (String. response))
             (replace-worker! worker-doc)))
         result-channel)))))
