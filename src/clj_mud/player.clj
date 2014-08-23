(ns clj-mud.player
  (:require [clj-mud.world :refer :all]
            [clj-mud.room :refer [find-room]]))

(defrecord Player [id name awake location])

(defn find-player
  [player-id]
  (some #(if (= player-id (:id (deref %))) %) @players))

(defn find-player-by-name
  [name]
  (some #(if (= name (:name (deref %))) %) @players))

(defn make-player
  ([name]
     (let [default-start-room-id (:start-room-id @config)]
       (if default-start-room-id
         (make-player name default-start-room-id))))
  ([name location-id]
     (log (str "Making player " name " at location " location-id))
     (if (and (find-room location-id)
              (nil? (find-player-by-name name)))
       (let [player (atom (Player. (inc-id) name false location-id))]
         (swap! players conj player)
         player))))

(defn move-player
  [player room]
  (log (str "Moving player " (:name @player) " to location "
            (:name @room) " (#" (:id @room) ")"))
  (swap! player assoc :location (:id @room)))

(defn players-at
  [room]
  (clojure.set/select #(= (:id @room) (:location @%)) @players))

(defn find-player-by-channel
  [ch]
  (let [handle (get @client-channels ch)]
    (if (and handle (:player-id @handle))
      (find-player (:player-id @handle)))))

(defn player-location
  [player]
  (if (:location @player)
    (find-room (:location @player))))

(defn remove-player
  [player-id]
  (let [player (find-player player-id)]
    (if player
      (do
        (swap! players disj player)
        player))))
