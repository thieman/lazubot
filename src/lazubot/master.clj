(ns lazubot.master
  (:require [clojure.java.io :as io]
            [clojure.java.shell :refer [sh]]
            [clojure.tools.logging :refer [warn]]
            [clojure.set :refer [difference]]
            [clojure.core.async :refer [<! >! go chan sliding-buffer]]
            [clojure-zulip.core :as zulip]
            [clojail.core :refer [sandbox]]
            [com.keminglabs.zmq-async.core :refer [register-socket!]])
  (:import [java.io PushbackReader]))

(def config (with-open [r (io/reader (io/resource "private/config"))]
              (read (PushbackReader. r))))
(def conn (zulip/connection config))
(def bot-streams ["test-stream"])
(def num-goroutines 10)
(def sb (sandbox [])) ;; sandbox without any testers, only using timeout capability

(defn open-socket []
  (let [addr "tcp://127.0.0.1:8080"
        [request-in request-out] (repeatedly 2 #(chan (sliding-buffer 10)))]
    (register-socket! {:in request-in :out request-out :socket-type :req
                       :configurator (fn [socket] (.bind socket addr))})
    (println (:out (sh "docker" "build" "-no-cache=true" "-t=lazubot-worker" "-" :in (slurp (io/resource "public/WorkerDockerfile")))))
    (println (:out (sh "docker" "run" "-rm=true" "-t" "-i" "-name client" "-link master:linked-master"
        "lazubot-worker")))
    (go
     (>! request-in "hello there socket!")
     (println (<! request-out)))))

(defn ensure-subscriptions []
  (zulip/sync* (zulip/add-subscriptions conn bot-streams))
  (let [current-subs (set (map :name (:subscriptions (zulip/sync* (zulip/subscriptions conn)))))
        remove-subs (difference current-subs (set bot-streams))]
    (when (seq remove-subs)
      (zulip/sync* (zulip/remove-subscriptions conn remove-subs)))))

(defn respond [message reply]
  (let [str-reply (if (seq? reply) (str (seq reply)) (str reply))
        parsed-reply (str "~~~clojure\n" str-reply "\n~~~")]
    (if (= "private" (get-in message [:message :type]))
      (zulip/send-private-message conn
                                  (map :email (get-in message [:message :display_recipient]))
                                  parsed-reply)
      (zulip/send-stream-message conn
                                 (get-in message [:message :display_recipient])
                                 (get-in message [:message :subject])
                                 parsed-reply))))

(defn extract-forms
  "Return a seq of suspected Clojure forms from a message."
  [content]
  (loop [input content
         current-form ""
         forms (vector)
         inside-escape? false
         inside-quote? false
         depth 0]
    (let [current-char (first input)]
      (case current-char
        nil forms
        \( (recur (rest input) (str (if (zero? depth) "" current-form) current-char) forms
                  inside-escape? inside-quote? (inc depth))
        \) (when (and (not inside-escape?) (not inside-quote?))
             (if (= depth 1)
               (recur (rest input) "" (conj forms (str current-form current-char))
                      inside-escape? inside-quote? (dec depth))
               (recur (rest input) (str current-form current-char) forms
                      inside-escape? inside-quote? (max 0 (dec depth)))))
        \" (recur (rest input) (str current-form current-char) forms
                  false (if inside-escape? inside-quote? (not inside-quote?)) depth)
        \\ (recur (rest input) (str current-form current-char) forms
                  (not inside-escape?) inside-quote? depth)
        (recur (rest input) (str current-form current-char) forms
               false inside-quote? depth)))))

(defn eval-form [form]
  (try
    (sb (read-string form))
    (catch Exception e
      e)))

(defn allowed-form?
  "Return bool of whether a form is contained within a code block."
  [content form]
  (let [regexes [(re-pattern (str "~~~(.|\\n)*\\Q" form "\\E(.|\\n)*~~~"))
                 (re-pattern (str "'\\Q" form "\\E"))
                 (re-pattern (str "`(.|\\n)*\\Q" form "\\E(.|\\n)*`"))]]
    (some #(re-find % content) regexes)))

(defn process-message [message]
  (when (and (= "message" (:type message))
             (not= (get-in message [:message :sender_email])
                   (get-in conn [:opts :username])))
    (let [content (get-in message [:message :content])
          forms (extract-forms content)
          allowed-forms (filter (partial allowed-form? content) forms)]
      (when (seq allowed-forms)
        (let [reply (interpose "\n\n" (map #(str % "\n" "=> " (eval-form %)) allowed-forms))]
          (respond message (apply str reply)))))))

(defn -main []
  (ensure-subscriptions)
  (open-socket)
  (let [queue-id (:queue_id (zulip/sync* (zulip/register conn ["message"])))
        messages (zulip/subscribe-events conn queue-id)]
    (dotimes [n num-goroutines]
      (go (loop []
                  (try
                    (process-message (<! messages))
                    (catch Exception e
                      (warn e)))
                  (recur)))))
  (println "Lazubot listening for messages"))
