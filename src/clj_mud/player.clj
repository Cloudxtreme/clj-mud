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
     (if (and (find-room location-id)
              (nil? (find-player-by-name name)))
       (let [player (atom (Player. (inc-id) name false location-id))]
         (swap! players conj player)
         player))))

(defn remove-player
  [player-id]
  (let [player (find-player player-id)]
    (if player
      (do
        (swap! players disj player)
        player))))
