(ns clj-mud.player-test
  (:require [clojure.test :refer :all]
            [clj-mud.room :refer :all]
            [clj-mud.player :refer :all]
            [clj-mud.world :refer :all]
            [clj-mud.core :as core]
            [clj-mud.test-helper :as test-helper])
  (:import clj_mud.player.Player))

(defn make-hall [] (make-room "The Hall" "The hall is long"))
(defn make-lounge [] (make-room "The Lounge" "The lounge is pretty OK"))
(defn make-bob
  ([start-room-id] (make-player "Bob" start-room-id))
  ([] (make-player "Bob")))
(defn make-alice [] (make-player "Alice"))

(use-fixtures :each
  (fn [f]
    (test-helper/reset-global-state)
    (make-hall)
    (make-lounge)
    (f)))

(def player-bob (Player. 3 "Bob" false 1))
(def player-alice (Player. 4 "Alice" false 1))

(testing "Making and Removing Players"
  (deftest test-make-player-returns-the-created-player
    (is (= player-bob @(make-bob))))

  (deftest test-make-player-adds-player-to-the-players-collection
    (is (empty? @players))
    (make-bob)
    (is (= 1 (count @players)))
    (is (= player-bob @(first @players))))

  (deftest test-remove-player-returns-the-removed-player
    (make-bob)
    (is (= player-bob @(remove-player 3))))

  (deftest test-removing-player-removes-from-players-collection
    (make-bob)
    (is (= 1 (count @players)))
    (is (= player-bob @(first @players)))
    (remove-player 3)
    (is (empty? @players)))

  (deftest test-player-starts-in-configured-starting-room-if-no-room-arg
    (make-alice)
    (is (= 1 (:location @(find-player 3))))
    (swap! config assoc :start-room-id 2)
    (make-bob)
    (is (= 2 (:location @(find-player 4)))))

  (deftest test-player-starts-in-specified-room-if-passed
    (make-bob 2)
    (is (= 2 (:location @(find-player 3)))))

  (deftest test-player-cannot-be-created-if-start-room-does-not-exist
    (is (nil? (make-bob 99)))
    (is (empty? @players)))

  (deftest cannot-make-two-players-with-the-same-name
    (is (= 0 (count @players)))
    (is (= player-bob @(make-player "Bob" 1)))
    (is (= 1 (count @players)))
    (is (= nil (make-player "Bob" 1)))
    (is (= 1 (count @players)))))

(testing "Moving Players"
  (deftest test-location-returns-current-location
    (make-bob)
    (is (= 1 (:location @(find-player-by-name "Bob"))))
    (move-player (find-player-by-name "Bob") (find-room 2))))

(testing "Finding Players"
  (deftest test-find-player-returns-player-if-exists
    (make-bob)
    (make-alice)
    (is (= player-bob @(find-player 3)))
    (is (= player-alice @(find-player 4))))

  (deftest test-find-player-returns-nil-if-player-does-not-exist
    (make-bob)
    (make-alice)
    (is (nil? (find-player 999))))

  (deftest test-find-player-by-name
    (make-bob)
    (make-alice)
    (is (= player-bob @(find-player-by-name "Bob")))
    (is (= player-alice @(find-player-by-name "Alice")))
    (is (nil? (find-player-by-name "Joe")))))
