(ns clj-mud.player
  (:require [clj-mud.world :refer :all]
            [clj-mud.rooms :refer [find-room]]
            [clj-mud.core :refer [config]]))

(defn make-player
  ([name]
     (let [default-start-room-id (:start-room-id @config)]
       (if default-start-room-id
         (make-player name default-start-room-id))))
  ([name location-id]
     (if (find-room location-id)
       (let [player (atom {:id (inc-id)
                           :name name
                           :awake false
                           :location location-id})]
         (do
           (swap! players conj player)
           player)))))

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
