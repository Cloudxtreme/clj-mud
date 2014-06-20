(ns clj-mud.player
  (:require [clj-mud.world :refer :all]
            [clj-mud.room :refer [find-room]]
            [clj-mud.core :refer [config]]))

(defrecord Player [id name awake location])

(defn make-player
  ([name]
     (let [default-start-room-id (:start-room-id @config)]
       (if default-start-room-id
         (make-player name default-start-room-id))))
  ([name location-id]
     (if (find-room location-id)
       (let [player (atom (Player. (inc-id) name false location-id))]
         (swap! players conj player)
         player))))

(defn find-player
  [player-id]
  (some #(if (= player-id (:id (deref %))) %) @players))

(defn remove-player
  [player-id]
  (let [player (find-player player-id)]
    (if player
      (do
        (swap! players disj player)
        player))))
