(ns clj-mud.core
  (:require [clojure.java.io :as io]
            [clojure.edn :as edn]
            [clj-mud.world :refer [current-room exits inc-id rooms client-channels]]
            [clj-mud.room :refer :all]
            [clojure.string :as string]
            [clj-time.core :as t]
            [clj-time.local :as l]
            [lamina.core :refer :all]
            [aleph.tcp :refer :all]
            [gloss.core :refer :all])
  (:gen-class))

(def command-handlers (atom {})) ;; Registered command handlers

;; Default configuration
(def config (atom {:mud-name "ClojureMud"
                   :start-room-id 1
                   :port 8888
                   :bind-address "0.0.0.0"}))

(defn log
  "Log a message to standard out"
  [message]
  (println (str "[" (l/local-now) "]: " message)))

(defn load-config
  "Try to load the specified configuration file, overriding the default config."
  [conf-file]
  (try
    (with-open [rdr (-> (io/resource conf-file)
                        io/reader
                        java.io.PushbackReader.)]
      (compare-and-set! config @config (merge @config (edn/read rdr))))
    (catch Exception e (str "Unable to load configuration file: " (.getMessage e)))))

(defn notify
  "Send a message to the specified channel."
  [ch & message]
  (if message
    (enqueue ch (str (apply str message)))
    (enqueue ch "\n")))

(defn normalize-input
  "Normalize input by changing command shortcuts to full command names."
  [line]
  (let [trimmed-line (string/trim line)]
    (case (first trimmed-line)
      \" (str "say " (subs trimmed-line 1))
      \: (str "pose " (subs trimmed-line 1))
      ;; Default
      trimmed-line)))

(defn tokenize-command
  "Tokenize the commnd input."
  [line]
  (let [split-line (string/split (normalize-input line) #"\s" 2)]
    (if (not (empty? split-line))
      (let [trigger (keyword (first split-line))]
        (if (contains? (set (keys @command-handlers)) trigger)
          (if (= 1 (count split-line))
            (list trigger)
            (list trigger (second split-line))))))))

(defn look [ch room]
  (notify ch (:name @room))
  (notify ch "\n")
  (notify ch (:desc @room))
  (notify ch "\n")
  (notify ch (str "    Exits: " (string/join ", " (get-exit-names room)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Commands are stored as a map of triggers to command handlers.
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn register-command
  [command handler]
  (swap! command-handlers assoc command handler))

(defn help-handler
  [ch & args]
  (notify ch "Oh dear. Don't be silly. Help isn't implemented yet.")
  (notify ch "(but seriously, type 'quit' to quit)"))

;; To be improved when multi-user support is added
(defn say-handler
  [ch args]
  (notify ch (str "You say, \"" args "\"")))

;; To be improved when multi-user support is added
(defn pose-handler
  [ch args]
  (notify ch (str "*** [" args "]")))

(defn look-handler
  [ch & args]
  (if (nil? @current-room)
    (notify ch "You don't see that here")
    (look ch @current-room)))

(defn walk-handler
  [ch direction]
  (let [exit (find-exit-by-name @current-room direction)]
    (if (nil? exit)
      (notify ch "There's no exit in that direction!")
      (do
        (move-to (find-room (:to exit)))
        (look ch @current-room)))))

(defn setup-world
  "Builds a very simple starter world. "
  []
  (register-command :help help-handler)
  (register-command :look look-handler)
  (register-command :go walk-handler)
  (register-command :say say-handler)
  (register-command :pose pose-handler)

  (def wizard-den
    (make-room "The Center of the Universe" "The Room at the Center of it All"))
  (def hallway
    (make-room "Hallway" "A long hallway."))
  (def foyer
    (make-room "Foyer" "A Foyer."))
  (def bedroom
    (make-room "Bedroom" "It's a bedroom."))

  (make-exit wizard-den hallway "east")
  (make-exit hallway wizard-den "west")
  (make-exit hallway foyer "east")
  (make-exit foyer hallway "west")
  (make-exit foyer bedroom "north")
  (make-exit bedroom foyer "south")

  (move-to wizard-den))

(defn- get-args [command]
  (if (not (nil? command))
    (second command)))

(defn- get-handler [command]
  (if (not (nil? command))
    ((first command) @command-handlers)))

(defn dispatch-command
  [ch input]
  (if (not (empty? input))
    (let [command (tokenize-command input)]
      (if (nil? command)
        (notify ch "Huh? (Type \"help\" for help)")
        ((get-handler command) ch (get-args command))))))

(defn read-one-line
  "The main MUD input handler"
  [ch]
  (receive-all
   ch
   (fn [input]
     (if (not (nil? input))
       (let [trimmed (string/trim input)]
         (if (= :quit (keyword trimmed))
           (close ch)
           ;; Else, dispatch the command
           (dispatch-command ch trimmed)))))))

(defn channel-connected
  ""
  [ch client-info]
  (swap! client-channels assoc ch client-info))

(defn channel-disconnected
  ""
  [ch]
  (swap! client-channels dissoc ch))

(defn client-handler [ch client-info]
  (log (str "Connection from " client-info))
  (on-closed ch (fn [] (log (str "Disconnected from " client-info))))
  (read-one-line ch))

(defn -main
  [& args]
  (log "Loading configuration...")
  (load-config "config.clj")
  (log "Setting up the world...")
  (setup-world)
  (let [numrooms (count @rooms)]
    (log (str "The world now has " numrooms
              (if (> numrooms 1) " rooms" " room"))))
  (log (str "Booting network, address=" (:bind-address @config)
            ", port=" (:port @config)))
  (try
    (start-tcp-server
     client-handler {:port (:port @config)
                     :host (:bind-address @config)
                     :frame (string :utf-8 :delimiters ["\r\n"])})
    (catch Exception e (str "caught exception: " (.getMessage e)))))
