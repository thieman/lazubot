(ns lazubot.core
  (:require [clojure.java.io :as io]
            [clojure.tools.logging :refer [warn]]
            [clojure.core.async :as async]
            [clojure-zulip.core :as zulip]
            [clojail.core :refer [sandbox]]
            [clojail.testers :refer [secure-tester]])
  (:import [java.io PushbackReader]))

(def config (with-open [r (io/reader (io/resource "private/config"))]
              (read (PushbackReader. r))))
(def conn (zulip/connection config))
(def bot-streams ["test-stream" "clojure" "ClojureScript Compiler"
                  "code review" "commits" "Victory"])
(def num-goroutines 10)
(def sb (sandbox secure-tester))

(defn ensure-subscriptions []
  (zulip/sync* (zulip/add-subscriptions conn bot-streams)))

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

(defn process-message [message]
  (when (and (= "message" (:type message))
             (not= (get-in message [:message :sender_email])
                   (get-in conn [:opts :username])))
    (let [content (get-in message [:message :content])]
      (when (= (first "(") (first content))
        (respond message (sb (read-string content)))))))

(defn -main []
  (ensure-subscriptions)
  (let [queue-id (:queue_id (zulip/sync* (zulip/register conn ["message"])))
        messages (zulip/subscribe-events conn queue-id)]
    (dotimes [n num-goroutines]
      (async/go (loop []
                  (try
                    (process-message (async/<! messages))
                    (catch Exception e
                      (warn e)))
                  (recur)))))
  (println "Lazubot listening for messages"))
