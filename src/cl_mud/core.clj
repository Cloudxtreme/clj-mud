(ns cl-mud.core
  (:require [clojure.java.io :as io]
            [clojure.edn :as edn]
            [cl-mud.world :refer [current-room exits inc-id rooms]]
            [cl-mud.rooms :refer :all]
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
  (notify (:name room))
  (notify)
  (notify (:desc room))
  (notify)
  (notify "    Exits:" (join ", " (get-exit-names (:id room)))))

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
  (if (not (nil? current-room))
    (look @@current-room)
    (notify "You don't see that here")))

(defn walk-handler
  [direction]
  (let [exit (find-exit-by-name (:id @@current-room) direction)]
    (if (nil? direction)
      (notify "Where?")
      (if (not (nil? exit))
        (do
          (move-to (:to exit))
          (look @@current-room))
        (notify "There's no exit in that direction!")))))

(defn setup-world
  "Builds a very simple starter world. "
  []
  (register-command :help help-handler)
  (register-command :look look-handler)
  (register-command :go walk-handler)
  (register-command :say say-handler)
  (register-command :pose pose-handler)

  (make-room "The Center of the Universe" "The Room at the Center of it All")
  (make-room "Hallway" "A long hallway.")
  (make-room "Foyer" "A Foyer.")
  (make-room "Bedroom" "It's a bedroom.")

  (make-exit 1 2 "east")  ; Den east to Hallway
  (make-exit 2 1 "west")  ; Hallway west to Den
  (make-exit 2 3 "east")  ; Hallway east to Garden
  (make-exit 3 2 "west")  ; Garden west to Hallway
  (make-exit 3 4 "north") ; Garden north to Bathroom
  (make-exit 4 3 "south") ; Bathroom south to Garden

  (move-to 1))

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
