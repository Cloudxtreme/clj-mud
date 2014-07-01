(ns clj-mud.world
  (:require [clojure.edn :as edn]
            [clj-time.core :as t]
            [clj-time.local :as l]
            [clojure.java.io :as io]))

(def next-id (atom 0))
(def rooms (atom #{}))
(def exits (atom #{}))
(def players (atom #{}))
(def player-locations (ref #{}))
(def room-contents (ref #{}))
(def player-credentials (atom #{}))
(def client-channels (atom {}))

(defrecord PlayerHandle [player-id client-info])

(defn inc-id
  "Return the next global object id"
  []
  (do
    (swap! next-id inc)
    @next-id))

;; Default configuration
(def config (atom {:mud-name "ClojureMud"
                   :start-room-id 1
                   :port 8888
                   :bind-address "0.0.0.0"}))

(defn load-config
  "Try to load the specified configuration file, overriding the default config."
  [conf-file]
  (try
    (with-open [rdr (-> (io/resource conf-file)
                        io/reader
                        java.io.PushbackReader.)]
      (compare-and-set! config @config (merge @config (edn/read rdr))))
    (catch Exception e (str "Unable to load configuration file: " (.getMessage e)))))

(defn log
  "Log a message to standard out"
  [message]
  (println (str "[" (l/local-now) "]: " message)))
