(defproject lazubot "0.1.0-SNAPSHOT"
  :description "A Clojure code execution bot for Zulip"
  :url "https://github.com/tthieman/lazubot"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/tools.logging "0.2.6"]
                 [org.clojure/core.async "0.1.262.0-151b23-alpha"]
                 [clojure-zulip "0.1.0-SNAPSHOT"]
                 [clojail "1.0.6"]
                 [com.keminglabs/zmq-async "0.1.0"]]
  :profiles {:master {:main lazubot.master}
             :worker {:main lazubot.worker}})
