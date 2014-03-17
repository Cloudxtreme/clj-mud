(ns cl-mud.world)

(def next-id (atom 0))
(def rooms (atom #{}))
(def current-room (atom nil))
(def exits (atom #{}))

(defn inc-id
  "Return the next global object id"
  []
  (do
    (swap! next-id inc)
    @next-id))
