(ns cl-mud.rooms-test
  (:require [clojure.test :refer :all]
            [cl-mud.world :refer :all]
            [cl-mud.rooms :refer :all]
            [cl-mud.test-helper :as test-helper]))

(def eg-den {:id 1 :name "The Den" :desc "The Den is nice"})
(def eg-hall {:id 2 :name "The Hall" :desc "The Hallway is long"})

(defn make-den [] (make-room "The Den" "The Den is nice"))
(defn make-hall [] (make-room "The Hall" "The Hallway is long"))
(defn make-garden [] (make-room "Garden" "A very lovely garden"))

(defn make-world []
  (let [den (make-den)
        hall (make-hall)
        garden (make-garden)]
    (make-exit den hall "east")
    (make-exit hall den "west")
    (make-exit hall garden "up")
    (make-exit garden hall "down")))

(use-fixtures :each
  (fn [f]
    (test-helper/reset-global-state)
    (f)))

(testing "Making and Removing Rooms"
  (deftest test-make-returns-the-created-room
    (is (= eg-den @(make-den)))
    (is (= eg-hall @(make-hall))))

  (deftest test-remove-room-returns-the-removed-room
    (make-den)
    (make-hall)

    (is (= eg-den @(remove-room (find-room 1))))
    (is (= eg-hall @(remove-room (find-room  2))))))

(testing "The Rooms Collection"
  (deftest test-make-adds-to-rooms-collection
    (is (empty? @rooms))
    (is (nil? (find-room 1)))
    (is (nil? (find-room 2)))

    (make-den)
    (is (= 1 (count @rooms)))
    (is (= eg-den @(find-room 1)))
    (is (nil? (find-room 2)))

    (make-hall)
    (is (= 2 (count @rooms)))
    (is (= eg-den @(find-room 1)))
    (is (= eg-hall @(find-room 2))))

  (deftest test-remove-room-removes-from-room-collection
    (make-den)
    (make-hall)

    (is (= 2 (count @rooms)))
    (is (= eg-den @(find-room 1)))
    (is (= eg-hall @(find-room 2)))

    (remove-room (find-room 2))
    (is (= 1 (count @rooms)))
    (is (= eg-den @(find-room 1)))
    (is (nil? (find-room 2)))

    (remove-room (find-room 1))
    (is (empty? @rooms))
    (is (nil? (find-room 1)))
    (is (nil? (find-room 2)))))

(testing "Finding rooms by id"
  (deftest find-room-returns-the-room-by-id
    (make-den)
    (make-hall)
    (is (= eg-den @(find-room 1)))
    (is (= 1 (:id @(find-room 1))))
    (is (= eg-hall @(find-room 2)))
    (is (= 2 (:id @(find-room 2)))))

  (deftest find-room-returns-nil-if-room-not-found
    (make-den)
    (make-hall)
    (is (nil? (find-room 999)))))

(testing "Linking Rooms"
  (deftest make-exit-adds-to-exits-collection
    (let [den (make-den)
          hall (make-hall)]
      (is (= 0 (count @exits)))
      (make-exit den hall "east")
      (is (= 1 (count @exits)))
      (make-exit hall den "west")
      (is (= 2 (count @exits)))))

  (deftest test-get-exits-should-return-all-exits-for-a-room
    (make-world)
    (let [den (find-room 1)
          hall (find-room 2)
          garden (find-room 3)]

      (is (= '({:from 1, :to 2, :name "east"}) (get-exits den)))
      (is (= '({:from 2, :to 1, :name "west"}
               {:from 2, :to 3, :name "up"}) (get-exits hall)))
      (is (= '({:from 3, :to 2, :name "down"}) (get-exits garden)))))

  (deftest test-get-exit-names-should-return-correct-names
    (make-world)
    (let [den (find-room 1)
          hall (find-room 2)
          garden (find-room 3)]

      (is (= '("east") (get-exit-names den)))
      (is (= '("west" "up") (get-exit-names hall)))
      (is (= '("down") (get-exit-names garden)))))

  (deftest test-find-exit-by-name-should-return-exit
    (make-world)
    (let [den (find-room 1)
          hall (find-room 2)
          garden (find-room 3)]
      (is (= {:from 1, :to 2, :name "east"} (find-exit-by-name den "east")))
      (is (nil? (find-exit-by-name den "west")))
      (is (= {:from 2, :to 1, :name "west"} (find-exit-by-name hall "west")))
      (is (= {:from 2, :to 3, :name "up"} (find-exit-by-name hall "up")))
      (is (nil? (find-exit-by-name hall "down")))
      (is (nil? (find-exit-by-name hall "east")))
      (is (= {:from 3, :to 2, :name "down"} (find-exit-by-name garden "down")))
      (is (nil? (find-exit-by-name garden "west"))))))

(testing "Navigating the World"
  (deftest test-move-sets-current-room
    (is (nil? @current-room))
    (let [den (make-den)
          hall (make-hall)]
      (move-to den)
      (is (= eg-den @@current-room))
      (move-to hall)
      (is (= eg-hall @@current-room))))

  (deftest test-move-returns-new-room
    (let [den (make-den)
          hall (make-hall)]
      (is (= eg-den @(move-to den)))
      (is (= eg-hall @(move-to hall))))))

(testing "Room Mutability"
  (deftest rename-room-changes-room-name-at-atom-level
    (make-den)
    (let [my-room-ref (find-room 1)]
      (is (= "The Den" (:name @(find-room 1))))
      (is (= "The Den" (:name @my-room-ref)))
      (rename-room (find-room 1) "The Living Room")
      (is (= "The Living Room" (:name @my-room-ref)))
      (is (= "The Living Room" (:name @(find-room 1))))
      (rename-room my-room-ref "The Bathroom")
      (is (= "The Bathroom" (:name @my-room-ref)))
      (is (= "The Bathroom" (:name @(find-room 1))))))

  (deftest describe-room-changes-room-description-at-atom-level
    (make-den)
    (let [my-room-ref (find-room 1)]
      (is (= "The Den is nice" (:desc @(find-room 1))))
      (is (= "The Den is nice" (:desc @my-room-ref)))
      (describe-room (find-room 1) "The Den is really filthy right now!")
      (is (= "The Den is really filthy right now!" (:desc @my-room-ref)))
      (is (= "The Den is really filthy right now!" (:desc @(find-room 1))))
      (describe-room my-room-ref "Oh wow, it's so bright")
      (is (= "Oh wow, it's so bright" (:desc @my-room-ref)))
      (is (= "Oh wow, it's so bright" (:desc @(find-room 1)))))))
