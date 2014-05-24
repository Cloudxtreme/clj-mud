(ns clj-mud.rooms
  (:require [clj-mud.world :refer :all]))

(defn make-room
  "Make a Room and put it in the world"
  [name desc]
  (let [room (atom {:id (inc-id), :name name, :desc desc})]
    (do
      (swap! rooms conj room)
      room)))

(defn find-room
  "Find a room in the rooms by id, or nil if no such room exists."
  [id]
  (some #(if (= id (:id (deref %))) %) @rooms))

(defn remove-room
  "Remove a room from the world"
  [room-atom]
  (do
    (swap! rooms disj room-atom)
    room-atom))

(defn move-to
  "Move the player to a given room"
  [room]
  (do
    (compare-and-set! current-room @current-room room)
    @current-room))

(defn make-exit
  "Makes an exit from the source room to the destination room"
  [from to name]
  (let [exit {:from (:id @from), :to (:id @to), :name name}]
    (do
      (swap! exits conj exit)
      exit)))

(defn get-exits
  "Returns all outgoing exits for the specified room."
  [room]
  (filter #(if (= (:id @room) (:from %)) %) @exits))

(defn get-exit-names
  "Returns a list of all outgoing exit names for the specified room."
  [room]
  (map :name (get-exits room)))

(defn find-exit-by-name
  "Returns the exit from the specified room with the given name, or
  nil if no exit could be found"
  [room name]
  (some #(if (and (= (:id @room) (:from %))
                  (= name (:name %))) %) @exits))

(defn- change-attrib
  "Changes an attribute on a room atomically"
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