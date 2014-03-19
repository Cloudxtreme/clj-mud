(ns cl-mud.core
  (:require [cl-mud.world :as world]
            [cl-mud.rooms :as rooms]
            [clojure.string :as string])
  (:gen-class))

(def command-handlers (atom {}))

(defn notify
  [& rest]
  (apply println rest))

(defn normalize-input
  [line]
  (let [trimmed-line (string/trim line)]
    (if (= \" (first trimmed-line))
      (str "say " (subs trimmed-line 1))
      (if (= \: (first trimmed-line))
        (str "pose " (subs trimmed-line 1))
        ;; Otherwise, normal case.
        trimmed-line))))

(defn parse-command
  [line]
  (let [split-line (string/split (normalize-input line) #"\s" 2)]
    (if (not (empty? split-line))
      (let [trigger (keyword (first split-line))]
        (if (contains? (set (keys @command-handlers)) trigger)
          (if (= 1 (count split-line))
            (list trigger)
            (list trigger (second split-line))))))))
  
(defn look [room]
  (notify (:name room))
  (notify)
  (notify (:desc room))
  (notify)
  (notify "    Exits:" (string/join ", " (rooms/get-exit-names (:id room)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Commands are stored as a map of triggers to command handlers.
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn register-command
  [command handler]
  (swap! command-handlers assoc command handler))

(defn help-handler
  [args]
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
  [args]
  (if (not (nil? world/current-room))
    (look @@world/current-room)
    (notify "You don't see that here")))

(defn walk-handler
  [direction]
  (let [exit (rooms/find-exit-by-name (:id @@world/current-room) direction)]
    (if (nil? direction)
      (notify "Where?")
      (if (not (nil? exit))
        (do
          (rooms/move-to (:to exit))
          (look @@world/current-room))
        (notify "There's no exit in that direction!")))))

(defn setup-world
  "Builds a very simple starter world. "
  []
  (register-command :help help-handler)
  (register-command :look look-handler)
  (register-command :go walk-handler)
  (register-command :say say-handler)
  (register-command :pose pose-handler)

  (rooms/make "The Center of the Universe" "The Room at the Center of it All")
  (rooms/make "Hallway" "A long hallway.")
  (rooms/make "Foyer" "A Foyer.")
  (rooms/make "Bedroom" "It's a bedroom.")

  (rooms/make-exit 1 2 "east")  ; Den east to Hallway
  (rooms/make-exit 2 1 "west")  ; Hallway west to Den
  (rooms/make-exit 2 3 "east")  ; Hallway east to Garden
  (rooms/make-exit 3 2 "west")  ; Garden west to Hallway
  (rooms/make-exit 3 4 "north") ; Garden north to Bathroom
  (rooms/make-exit 4 3 "south") ; Bathroom south to Garden

  (rooms/move-to 1))

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
      (let [trimmed (string/trim input)]
        (when (not= :quit (keyword trimmed))
          (dispatch-command trimmed)
          (recur))))))

(defn -main
  [& args]
  (notify "Setting up the world...")
  (setup-world)
  (let [numrooms (count @world/rooms)]
    (notify "The world now has" numrooms
             (if (> numrooms 1) "rooms" "room")))
  (notify "You are in:" (:name @@world/current-room))
  (notify "Running MUD")
  (main-loop)
  (notify "\n\nGoodbye!"))
