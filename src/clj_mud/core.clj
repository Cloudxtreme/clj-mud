(ns clj-mud.core
  (:require [clojure.string :as string]
            [clj-mud.world :refer :all]
            [clj-mud.room :refer :all]
            [clj-mud.player :refer :all]
            [clj-time.core :as t]
            [clj-time.local :as l]
            [lamina.core :refer :all]
            [aleph.tcp :refer :all]
            [gloss.core :refer :all])
  (:gen-class))

(def command-handlers (atom {})) ;; Registered command handlers

(defn log
  "Log a message to standard out"
  [message]
  (println (str "[" (l/local-now) "]: " message)))

(defn send-msg
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
  (send-msg ch (:name @room))
  (send-msg ch "")
  (send-msg ch (:desc @room))
  (send-msg ch "")
  (send-msg ch (str "    Exits: " (string/join ", " (get-exit-names room)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Commands are stored as a map of triggers to command handlers.
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn register-command
  [command handler]
  (swap! command-handlers assoc command handler))

(defn help-handler
  [ch & args]
  (send-msg ch "Oh dear. Don't be silly. Help isn't implemented yet.")
  (send-msg ch "(but seriously, type 'quit' to quit)"))

;; To be improved when multi-user support is added
(defn say-handler
  [ch args]
  (send-msg ch (str "You say, \"" args "\"")))

;; To be improved when multi-user support is added
(defn pose-handler
  [ch args]
  (send-msg ch (str "*** [" args "]")))

(defn look-handler
  [ch & args]
  (if (nil? @current-room)
    (send-msg ch "You don't see that here")
    (look ch @current-room)))

(defn walk-handler
  [ch direction]
  (if (nil? direction)
    (send-msg ch "Go where?")
    (let [exit (find-exit-by-name @current-room direction)]
      (if (nil? exit)
        (send-msg ch "There's no exit in that direction!")
        (do
          (move-to (find-room (:to exit)))
          (look ch @current-room))))))

(defn connect-handler
  [ch name]
  (make-player name))

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
        (send-msg ch "Huh? (Type \"help\" for help)")
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
  (log (str "Connection from " client-info))
  (send-msg ch "")
  (send-msg ch "---------------------------------------------------------------")
  (send-msg ch "* Welcome to this Experimental Clojure Mud!                   *")
  (send-msg ch "---------------------------------------------------------------")
  (send-msg ch "")
  (send-msg ch "Commands are: look, go, quit")
  (send-msg ch "")
  (send-msg ch "Ready.")
  (swap! client-channels assoc ch client-info)
  (log (str "Channels now: " @client-channels)))

(defn channel-disconnected
  ""
  [ch client-info]
  (log (str "Disconnect from " client-info))
  (swap! client-channels dissoc ch)
  (log (str "Channels now: " @client-channels)))

(defn client-handler [ch client-info]
  (channel-connected ch client-info)
  (on-closed ch (fn [] (channel-disconnected ch client-info)))
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
