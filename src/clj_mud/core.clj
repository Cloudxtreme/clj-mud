(ns clj-mud.core
  (:require [clojure.java.io :as io]
            [clojure.edn :as edn]
            [clj-mud.world :refer [current-room exits inc-id rooms]]
            [clj-mud.rooms :refer :all]
            [clojure.string :refer [join split trim]])
  (:gen-class))

(def command-handlers (atom {}))

;; Default configuration
(def config (atom {:mud-name "ClojureMud"
                   :start-room-id 1
                   :port 8888
                   :bind-address "0.0.0.0"}))

(defn load-config
  [conf-file]
  (try
    (with-open [rdr (-> (io/resource conf-file)
                        io/reader
                        java.io.PushbackReader.)]
      (compare-and-set! config @config (merge @config (edn/read rdr))))
    (catch Exception e (str "Unable to load configuration file: " (.getMessage e)))))

(defn notify
  [& rest]
  (apply println rest))

(defn normalize-input
  [line]
  (let [trimmed-line (trim line)]
    (if (= \" (first trimmed-line))
      (str "say " (subs trimmed-line 1))
      (if (= \: (first trimmed-line))
        (str "pose " (subs trimmed-line 1))
        ;; Otherwise, normal case.
        trimmed-line))))

(defn parse-command
  [line]
  (let [split-line (split (normalize-input line) #"\s" 2)]
    (if (not (empty? split-line))
      (let [trigger (keyword (first split-line))]
        (if (contains? (set (keys @command-handlers)) trigger)
          (if (= 1 (count split-line))
            (list trigger)
            (list trigger (second split-line))))))))

(defn look [room]
  (notify (:name @room))
  (notify)
  (notify (:desc @room))
  (notify)
  (notify (str "    Exits: " (join ", " (get-exit-names room)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Commands are stored as a map of triggers to command handlers.
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn register-command
  [command handler]
  (swap! command-handlers assoc command handler))

(defn help-handler
  [& args]
  (notify "Oh dear. Don't be silly. Help isn't implemented yet.")
  (notify "(but seriously, type 'quit' to quit)"))

;; To be improved when multi-user support is added
(defn say-handler
  [args]
  (notify (str "You say, \"" args "\"")))

;; To be improved when multi-user support is added
(defn pose-handler
  [args]
  (notify (str "*** [" args "]")))

(defn look-handler
  [& args]
  (if (nil? @current-room)
    (notify "You don't see that here")
    (look @current-room)))

(defn walk-handler
  [direction]
  (let [exit (find-exit-by-name @current-room direction)]
    (if (nil? exit)
      (notify "There's no exit in that direction!")
      (do
        (move-to (find-room (:to exit)))
        (look @current-room)))))

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
  [input]
  (if (not (empty? input))
    (let [command (parse-command input)]
      (if (nil? command)
        (notify "Huh? (Type \"help\" for help)")
        ((get-handler command) (get-args command))))))

(defn main-loop
  "The main MUD Loop"
  []
  (print "mud> ")
  (flush)
  (let [input (read-line)]
    (if (not (nil? input))
      (let [trimmed (trim input)]
        (when (not= :quit (keyword trimmed))
          (dispatch-command trimmed)
          (recur))))))

(defn -main
  [& args]
  (notify "Setting up the world...")
  (setup-world)
  (let [numrooms (count @rooms)]
    (notify "The world now has" numrooms
             (if (> numrooms 1) "rooms" "room")))
  (notify "You are in:" (:name @@current-room))
  (notify "Running MUD")
  (main-loop)
  (notify "\n\nGoodbye!"))
