(ns clj-mud.core-test
  (:require [clojure.test :refer :all]
            [clj-mud.core :refer :all]
            [clj-mud.rooms :refer :all]
            [clj-mud.world :refer :all]
            [clj-mud.test-helper :as test-helper]
            [lamina.core :refer :all]
            [aleph.tcp :refer :all]
            [gloss.core :refer :all]))

(def last-mock-arg (atom nil))
(def notifications (atom []))

;; Mock handler
(defn mock-handler
  [ch args]
  (compare-and-set! last-mock-arg @last-mock-arg (first args)))

(def mock-channel nil)

(defn mock-enqueue
  "Mock implementation of 'enqueue' that appends to notifications list"
  [ch arg]
  (swap! notifications conj arg))

(defmacro with-mock-enqueue
  "Convenience macro to help capture println output."
  [& args]
  `(with-redefs-fn {#'enqueue mock-enqueue} (fn [] ~@args)))

(use-fixtures :each
  (fn [f]
    (test-helper/reset-global-state)
    (compare-and-set! notifications @notifications [])
    (f)))

(testing "Configuration File Loading"
  (deftest default-config-sanity-check
    (is (= "ClojureMud" (:mud-name @config)))
    (is (= 1 (:start-room-id @config)))
    (is (= 8888 (:port @config)))
    (is (= "0.0.0.0" (:bind-address @config))))

  (deftest loading-non-existent-file-leaves-config-unchanged
    (load-config "no_such_file.clj")
    (is (= "ClojureMud" (:mud-name @config)))
    (is (= 1 (:start-room-id @config)))
    (is (= 8888 (:port @config)))
    (is (= "0.0.0.0" (:bind-address @config))))

  (deftest loaded-config-overrides-default-config
    (load-config "test_config_1.clj")
    (is (= "TestConfig1MudName" (:mud-name @config)))
    (is (= 2929 (:start-room-id @config)))
    (is (= 8765 (:port @config)))
    (is (= "127.0.0.1" (:bind-address @config))))

  (deftest loaded-config-merges-with-default-config
    (load-config "test_config_2.clj")
    (is (= "ClojureMud" (:mud-name @config)))
    (is (= 1181 (:start-room-id @config)))
    (is (= 8999 (:port @config)))
    (is (= "0.0.0.0" (:bind-address @config)))))

(testing "Parsing Commands"
  (deftest test-normalize-input-expands-single-quote-to-say
    (register-command :say mock-handler)
    (is (= "say hello there" (normalize-input "\"hello there"))))

  (deftest test-normalize-input-expands-single-colon-to-pose
    (register-command :pose mock-handler)
    (is (= "pose hello there" (normalize-input ":hello there"))))

  (deftest test-parse-command-should-normlize-text
    (register-command :say mock-handler)
    (register-command :pose mock-handler)
    (is (= '(:say "This is super fun!") (parse-command "\"This is super fun!")))
    (is (= '(:pose "foooooobar") (parse-command ":foooooobar"))))

  (deftest test-parse-command-should-split-string-into-list
    (register-command :say mock-handler)
    (register-command :pose mock-handler)
    (register-command :look mock-handler)
    (register-command :go mock-handler)
    (is (= '(:look "north") (parse-command "look north")))
    (is (= '(:say "This is super fun, too!") (parse-command "say This is super fun, too!")))
    (is (= '(:look) (parse-command "look"))))

  (deftest test-parse-command-should-return-nil-if-no-trigger-found
    (register-command :say mock-handler)
    (register-command :pose mock-handler)
    (is (nil? (parse-command "look north")))
    (is (= '(:say "bar") (parse-command "say bar")))))

(testing "Registering Commands"
  (deftest test-registering-a-command-should-append-to-command-handlers-atom
    (is (= {} @command-handlers))
    (register-command :say mock-handler)
    (is (= {:say mock-handler} @command-handlers))))

(testing "Bootstrapping the World"
  (deftest setup-world-builds-a-world
    (is (= 0 (count @rooms)))
    (is (= 0 (count @exits)))
    (setup-world)
    (is (>= (count @rooms) 1))
    (is (>= (count @exits) 1))))

(testing "Dispatching commands"
  (deftest dispatch-command-test
    (with-mock-enqueue (dispatch-command mock-channel "say Hello, world!"))
    (is (= "You say, \"Hello!\"") (last @notifications))))

(testing "Handlers"
  (deftest say-handler-test
    (with-mock-enqueue (say-handler mock-channel "Hello!"))
    (is (= "You say, \"Hello!\"") (last @notifications)))

  (deftest pose-handler-test
    (with-mock-enqueue (pose-handler mock-channel "flaps around the room."))
    (is (re-seq #"flaps around the room." (last @notifications))))

  (deftest look-handler-non-existent-rooms
    (with-mock-enqueue (look-handler mock-channel))
    (is (= ["You don't see that here"] @notifications)))

  (deftest look-handler-prints-current-room
    (let [den (make-room "The Den" "This is a nice den")
          hall (make-room "The Hall" "A Long Hallway")]
      (make-exit den hall "east")
      (make-exit hall den "west")
      (move-to den)
      (with-mock-enqueue (look-handler mock-channel))
      (is (= ["The Den"
              "\n"
              "This is a nice den"
              "\n"
              "    Exits: east"] @notifications))))

  (deftest walk-handler-moves-the-player
    (let [den (make-room "The Den" "This is a nice den")
          hall (make-room "The Hall" "A Long Hallway")]
      (make-exit den hall "east")
      (make-exit hall den "west")
      (move-to den)
      (is (= den @current-room))
      (with-mock-enqueue (walk-handler mock-channel "east"))
      (is (= hall @current-room))))

  (deftest walk-handler-wont-move-to-nonexistent-exit
    (let [den (make-room "The Den" "This is a nice den")
          hall (make-room "The Hall" "A Long Hallway")]
      (make-exit den hall "east")
      (make-exit hall den "west")
      (move-to den)
      (is (= den @current-room))
      (with-mock-enqueue (walk-handler mock-channel "north"))
      (is (= den @current-room))
      (is (= "There's no exit in that direction!" (last @notifications)))))

  (deftest walk-handler-looks-at-new-room
    (let [den (make-room "The Den" "This is a nice den")
          hall (make-room "The Hall" "A Long Hallway")]
      (make-exit den hall "east")
      (make-exit hall den "west")
      (move-to den)
      (with-mock-enqueue (walk-handler mock-channel "east"))
      (is (= ["The Hall"
              "\n"
              "A Long Hallway"
              "\n"
              "    Exits: west"] @notifications))))

  (deftest help-handler-returns-at-least-one-line
    "We don't really care what it returns, just that it returns something."
    (with-mock-enqueue (help-handler mock-channel))
    (is (<= 1 (count @notifications)))))

(testing "Connecting Channels"
  (deftest channels-are-added-to-channel-set-on-connection
    (channel-connected "Mock Channel" {:address "0:0:0:0:0:0:0:1%0"})
    (is (= {"Mock Channel" {:address "0:0:0:0:0:0:0:1%0"}} @client-channels)))

  (deftest channels-are-removed-from-channel-set-on-disconnect
    (channel-connected "Mock Channel" {:address "0:0:0:0:0:0:0:1%0"})
    (is (not (empty? @client-channels)))
    (channel-disconnected "Mock Channel")
    (is (empty? @client-channels))))
