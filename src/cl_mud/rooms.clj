(ns cl-mud.rooms
  (:require [cl-mud.world :as world]))

(defn make
  "Make a Room and put it in the world"
  [name desc]
  (let [room (atom {:id (world/inc-id), :name name, :desc desc})]
    (do
      (swap! world/rooms conj room)
      room)))

(defn find-room
  "Find a room in the rooms by id, or nil if no such room."
  [id]
  (some #(if (= id (:id (deref %))) %) @world/rooms))

(defn remove-room
  "Remove a room from the rooms"
  [room-id]
  (let [room (find-room room-id)]
    (if (not (nil? room))
      (do
        (swap! world/rooms disj room)
        room))))

(defn move-to
  "Move the player to a given room"
  [room-id]
  (let [room (find-room room-id)]
    (if (not (nil? room))
      (do
        (compare-and-set! world/current-room @world/current-room room)
        @world/current-room))))

(defn make-exit
  [from to name]
  (let [exit {:from from, :to to, :name name}]
    (do
      (swap! world/exits conj exit)
      exit)))

(defn get-exits
  "Returns all outgoing exits for the specified room."
  [room-id]
  (filter #(if (= room-id (:from %)) %) @world/exits))

(defn get-exit-names
  "Returns a list of all outgoing exit names for the specified room."
  [room-id]
  (map :name (get-exits room-id)))

(defn find-exit-by-name
  "Returns the exit from the specified room with the given name, or
  nil if no exit could be found"
  [room-id name]
  (some #(if (and (= room-id (:from %))
                  (= name (:name %))) %) @world/exits))

(defn- change-attrib
  [room attrib val]
  (do
    (swap! room assoc attrib val)
    room))

(defn rename-room
  [room new-name]
  (change-attrib room :name new-name))

(defn describe-room
  [room new-desc]
  (change-attrib room :desc new-desc))
