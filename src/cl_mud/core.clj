(ns cl-mud.core
  (:require [cl-mud.world :as world]
            [cl-mud.rooms :as rooms]
            [clojure.string :as string])
  (:gen-class))

(def command-handlers (atom {}))

(defn parse-command
  [line]
  (let [parsed (string/split line #"\s" 2)]
    (if (= 1 (count parsed))
      (list (keyword (first parsed)))
      (list (keyword (first parsed)) (second parsed)))))

(defn look [room]
  (println (:name room))
  (println)
  (println (:desc room))
  (println)
  (println "    Exits:" (rooms/get-exit-names (:id room))))

(defn register-command
  [command handler]
  (swap! command-handlers assoc command handler))

;; Handlers
(defn say-handler [args]
  (println "You said: " args))

(defn look-handler [args]
  (if (not (nil? world/current-room))
    (look @@world/current-room)
    (println "You don't see that here")))

(defn walk-handler [direction]
  (let [exit (rooms/find-exit-by-name (:id @@world/current-room) direction)]
    (if (not (nil? exit))
      (do
        (println "Walking" direction)
        (rooms/move-to (:to exit))
        (look @@world/current-room))
      (println "There's no exit in that direction!"))))

(defn setup-world []
  (register-command :say say-handler)
  (register-command :look look-handler)
  (register-command :walk walk-handler)

  (rooms/make "The Den" "The Den is lovely")
  (rooms/make "The Hallway" "The Hallway is lovely")
  (rooms/make "The Garden" "The Garden is full of plants")
  (rooms/make "The Bathroom" "There's a little bathtub here")

  (rooms/make-exit 1 2 "east")  ; Den east to Hallway
  (rooms/make-exit 2 1 "west")  ; Hallway west to Den
  (rooms/make-exit 2 3 "east")  ; Hallway east to Garden
  (rooms/make-exit 3 2 "west")  ; Garden west to Hallway
  (rooms/make-exit 3 4 "north") ; Garden north to Bathroom
  (rooms/make-exit 4 3 "south") ; Bathroom south to Garden

  (rooms/move-to 1))

(defn main-loop
  "The main MUD Loop"
  []
  (print "mud> ")
  (flush)
  (let [line (read-line)]
    (when
        (and 
         (not (nil? line))
         (not= :quit (keyword line)))

      (if (> (count line) 0)
        
        (let [command-and-args (parse-command line)
              command (first command-and-args)
              args (second command-and-args)
              handler (command @command-handlers)]

          (if (not (nil? handler))
            (handler args)
            (println "Huh?"))))
      
      (recur))))

(defn -main
  [& args]
  (println "Setting up the world...")
  (setup-world)
  (let [numrooms (count @world/rooms)]
    (println "The world now has" numrooms
             (if (> numrooms 1) "rooms" "room")))
  (println "You are in:" (:name @@world/current-room))
  (println "Running MUD")
  (main-loop)
  (println "\n\nGoodbye!"))
