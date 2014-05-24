(ns clj-mud.world)

(def next-id (atom 0))
(def rooms (atom #{}))
(def current-room (atom nil))
(def exits (atom #{}))
(def players (atom #{}))
(def player-locations (ref #{}))
(def room-contents (ref #{}))
(def player-credentials (atom #{}))

(defn inc-id
  "Return the next global object id"
  []
  (do
    (swap! next-id inc)
    @next-id))
