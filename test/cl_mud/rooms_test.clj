(ns cl-mud.rooms-test
  (:require [clojure.test :refer :all]
            [cl-mud.world :as world :refer :all]
            [cl-mud.rooms :refer :all]))

(defn reset-global-state [f]
  (compare-and-set! world/next-id @world/next-id 0)
  (compare-and-set! world/rooms @world/rooms #{})
  (compare-and-set! world/current-room @world/current-room nil)
  (compare-and-set! world/exits @world/exits #{})
  (f))

(def the-den {:id 1 :name "The Den" :desc "The Den is nice"})
(def the-hall {:id 2 :name "The Hall" :desc "The Hallway is long"})

(defn make-the-den [] (make "The Den" "The Den is nice"))
(defn make-the-hall [] (make "The Hall" "The Hallway is long"))
(defn make-garden [] (make "Garden" "A very lovely garden"))
(defn make-world []
  (make-the-den)
  (make-the-hall)
  (make-garden)
  (make-exit 1 2 "east")
  (make-exit 2 1 "west")
  (make-exit 2 3 "up")
  (make-exit 3 2 "down"))

(use-fixtures :each reset-global-state)

(testing "Making and Removing Rooms"
  (deftest test-make-returns-the-created-room
    (is (= the-den @(make-the-den)))
    (is (= the-hall @(make-the-hall))))
  
  (deftest test-remove-room-returns-the-removed-room
    (make-the-den)
    (make-the-hall)

    (is (= the-den @(remove-room 1)))
    (is (= the-hall @(remove-room 2)))))

(testing "The Rooms Collection"
  (deftest test-make-adds-to-rooms-collection
    (is (empty? @world/rooms))
    (is (nil? (find-room 1)))
    (is (nil? (find-room 2)))
    
    (make-the-den)
    (is (= 1 (count @world/rooms)))
    (is (= the-den @(find-room 1)))
    (is (nil? (find-room 2)))
    
    (make-the-hall)
    (is (= 2 (count @world/rooms)))
    (is (= the-den @(find-room 1)))
    (is (= the-hall @(find-room 2))))

  (deftest test-remove-room-removes-from-room-collection
    (make-the-den)
    (make-the-hall)

    (is (= 2 (count @world/rooms)))
    (is (= the-den @(find-room 1)))
    (is (= the-hall @(find-room 2)))
    
    (remove-room 2)
    (is (= 1 (count @world/rooms)))
    (is (= the-den @(find-room 1)))
    (is (nil? (find-room 2)))

    (remove-room 1)
    (is (empty? @world/rooms))
    (is (nil? (find-room 1)))
    (is (nil? (find-room 2)))))

(testing "Finding rooms by id"
  (deftest find-room-returns-the-room-by-id
    (make-the-den)
    (make-the-hall)
    (is (= the-den @(find-room 1)))
    (is (= 1 (:id @(find-room 1))))
    (is (= the-hall @(find-room 2)))
    (is (= 2 (:id @(find-room 2)))))

  (deftest find-room-returns-nil-if-room-not-found
    (make-the-den)
    (make-the-hall)
    (is (nil? (find-room 999)))))


(testing "Linking Rooms"
  (deftest make-exit-adds-to-exits-collection
    (make-the-den)
    (make-the-hall)
    (is (= 0 (count @world/exits)))
    (make-exit 1 2 "east")
    (is (= 1 (count @world/exits)))
    (make-exit 2 1 "west")
    (is (= 2 (count @world/exits))))

  (deftest test-get-exits-should-return-all-exits-for-a-room
    (make-world)

    (is (= '({:from 1, :to 2, :name "east"}) (get-exits 1)))
    (is (= '({:from 2, :to 1, :name "west"}
             {:from 2, :to 3, :name "up"}) (get-exits 2)))
    (is (= '({:from 3, :to 2, :name "down"}) (get-exits 3))))

  (deftest test-get-exit-names-should-return-correct-names
    (make-world)
    (is (= '("east") (get-exit-names 1)))
    (is (= '("west" "up") (get-exit-names 2)))
    (is (= '("down") (get-exit-names 3))))
  (deftest test-find-exit-by-name-should-return-exit
    (make-world)
    (is (= {:from 1, :to 2, :name "east"} (find-exit-by-name 1 "east")))
    (is (nil? (find-exit-by-name 1 "west")))
    (is (= {:from 2, :to 1, :name "west"} (find-exit-by-name 2 "west")))
    (is (= {:from 2, :to 3, :name "up"} (find-exit-by-name 2 "up")))
    (is (nil? (find-exit-by-name 2 "down")))
    (is (nil? (find-exit-by-name 2 "east")))
    (is (= {:from 3, :to 2, :name "down"} (find-exit-by-name 3 "down")))
    (is (nil? (find-exit-by-name 3 "west")))))

(testing "Navigating the World"
  (deftest test-move-sets-current-room
    (is (nil? @current-room))
    (make-the-den)
    (make-the-hall)
    (move-to 1)
    (is (= the-den @@current-room))
    (move-to 2)
    (is (= the-hall @@current-room)))

  (deftest test-move-returns-new-room
    (make-the-den)
    (make-the-hall)
    (is (= the-den @(move-to 1)))
    (is (= the-hall @(move-to 2))))

  (deftest test-move-nonexistent-room-leaves-current-room-unchanged
    (make-the-den)
    (make-the-hall)
    (move-to 1)
    (is (= the-den @@current-room))
    (move-to 999)
    (is (= the-den @@current-room)))

  (deftest test-move-nonexistent-room-returns-nil
    (make-the-den)
    (make-the-hall)
    (is (nil? (move-to 9999)))))

(testing "Room Mutability"
  (deftest rename-room-changes-room-name-at-atom-level
    (make-the-den)
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
    (make-the-den)
    (let [my-room-ref (find-room 1)]
      (is (= "The Den is nice" (:desc @(find-room 1))))
      (is (= "The Den is nice" (:desc @my-room-ref)))
      (describe-room (find-room 1) "The Den is really filthy right now!")
      (is (= "The Den is really filthy right now!" (:desc @my-room-ref)))
      (is (= "The Den is really filthy right now!" (:desc @(find-room 1))))
      (describe-room my-room-ref "Oh wow, it's so bright")
      (is (= "Oh wow, it's so bright" (:desc @my-room-ref)))
      (is (= "Oh wow, it's so bright" (:desc @(find-room 1)))))))
