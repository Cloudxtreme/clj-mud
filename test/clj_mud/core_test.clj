(ns clj-mud.core-test
  (:require [clojure.test :refer :all]
            [clj-mud.core :refer :all]
            [clj-mud.room :refer :all]
            [clj-mud.world :refer :all]
            [clj-mud.player :refer :all]
            [clj-mud.test-helper :refer :all])
  (:import clj_mud.world.PlayerHandle))

;; Mock handler
(defn mock-handler
  [ch args]
  (compare-and-set! last-mock-arg @last-mock-arg (first args)))

(use-fixtures :each
  (fn [f]
    (reset-global-state)
    (compare-and-set! notifications @notifications {})
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

  (deftest test-tokenize-command-should-normlize-text
    (register-command :say mock-handler)
    (register-command :pose mock-handler)
    (is (= '(:say "This is super fun!") (tokenize-command "\"This is super fun!")))
    (is (= '(:pose "foooooobar") (tokenize-command ":foooooobar"))))

  (deftest test-tokenize-command-should-split-string-into-list
    (register-command :say mock-handler)
    (register-command :pose mock-handler)
    (register-command :look mock-handler)
    (register-command :go mock-handler)
    (is (= '(:look "north") (tokenize-command "look north")))
    (is (= '(:say "This is super fun, too!") (tokenize-command "say This is super fun, too!")))
    (is (= '(:look) (tokenize-command "look"))))

  (deftest test-tokenize-command-should-return-nil-if-no-trigger-found
    (register-command :say mock-handler)
    (register-command :pose mock-handler)
    (is (nil? (tokenize-command "look north")))
    (is (= '(:say "bar") (tokenize-command "say bar")))))

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
    (with-mock-io
      (dispatch-command mock-channel "say Hello, world!")
      (is (= "You say, \"Hello!\"") (last (mock-channel @notifications))))))

(testing "Handlers"
  (deftest connect-handler-creates-player-if-needed
    (with-mock-io
      (make-room "The Hall" "The hall is long")
      (is (= 0 (count @players)))
      (is (= "bob" (:name @(connect-handler mock-channel "bob"))))
      (is (= 1 (count @players)))
      (is (= "bob" (:name @(last @players))))))

  (deftest connect-handler-finds-player-if-exists
    (make-room "The Hall" "The hall is long")
    (with-mock-io (make-player "bob" 1)))

  (deftest connect-handler-welcomes-player
    (with-mock-io
      (make-room "The Hall" "The hall is long")
      (make-player "bob" 1)
      (is (= 1 (count @players)))
      (connect-handler mock-channel "bob")
      (is (= "Welcome, bob!" (last (mock-channel @notifications))))))

  (deftest connect-handler-makes-player-awake
    (with-mock-io
      (make-room "The Hall" "The hall is long")
      (make-player "bob" 1)
      (is (false? (:awake @(find-player-by-name "bob"))))
      (connect-handler mock-channel "bob")
      (is (:awake @(find-player-by-name "bob")))))
  
  (deftest say-handler-test
    (with-mock-io (say-handler mock-channel "Hello!"))
    (is (= "You say, \"Hello!\"") (last (mock-channel @notifications))))

  (deftest pose-handler-test
    (with-mock-io (pose-handler mock-channel "flaps around the room."))
    (is (re-seq #"flaps around the room." (last (mock-channel @notifications)))))

  (deftest look-handler-non-existent-rooms
    (with-mock-io (look-handler mock-channel))
    (is (= ["You don't see that here"] (mock-channel @notifications))))

  (deftest look-handler-prints-current-room
    (with-mock-io
      (let [den (make-room "The Den" "This is a nice den")
            hall (make-room "The Hall" "A Long Hallway")]
        (make-exit den hall "east")
        (make-exit hall den "west")
        (move-to den)
        (look-handler mock-channel)
        (is (= ["The Den"
                ""
                "This is a nice den"
                ""
                "    Exits: east"] (mock-channel @notifications))))))

  (deftest walk-handler-moves-the-player
    (with-mock-io
      (let [den (make-room "The Den" "This is a nice den")
            hall (make-room "The Hall" "A Long Hallway")]
        (make-exit den hall "east")
        (make-exit hall den "west")
        (move-to den)
        (is (= den @current-room))
        (walk-handler mock-channel "east")
        (is (= hall @current-room)))))

  (deftest walk-handler-notifies-of-incorrect-usage
    (with-mock-io (walk-handler mock-channel nil))
    (is (= "Go where?" (last (get @notifications mock-channel)))))
  
  (deftest walk-handler-wont-move-to-nonexistent-exit
    (with-mock-io
      (let [den (make-room "The Den" "This is a nice den")
            hall (make-room "The Hall" "A Long Hallway")]
        (make-exit den hall "east")
        (make-exit hall den "west")
        (move-to den)
        (is (= den @current-room))
        (walk-handler mock-channel "north")
        (is (= den @current-room))
        (is (= "There's no exit in that direction!" (last (mock-channel @notifications)))))))

  (deftest walk-handler-looks-at-new-room
    (with-mock-io
      (let [den (make-room "The Den" "This is a nice den")
            hall (make-room "The Hall" "A Long Hallway")]
        (make-exit den hall "east")
        (make-exit hall den "west")
        (move-to den)
        (walk-handler mock-channel "east")
        (is (= ["The Hall"
                ""
                "A Long Hallway"
                ""
                "    Exits: west"] (mock-channel @notifications))))))

  (deftest help-handler-returns-at-least-one-line
    "We don't really care what it returns, just that it returns something."
    (with-mock-io (help-handler mock-channel))
    (is (<= 1 (count (mock-channel @notifications))))))

(testing "Connecting Channels"
  (deftest channels-are-added-to-channel-set-on-connection
    (is (= 0 (count @client-channels)))
    (with-mock-io (channel-connected mock-channel {:address "0:0:0:0:0:0:0:1%0"}))
    (is (= 1 (count @client-channels))))

  (deftest channels-are-removed-from-channel-set-on-disconnect
    (with-mock-io
      (channel-connected mock-channel {:address "0:0:0:0:0:0:0:1%0"})
      (is (not (empty? @client-channels)))
      (channel-disconnected mock-channel {:address "0:0:0:0:0:0:0:1%0"})
      (is (empty? @client-channels)))))

(testing "Player Connection"
  (deftest players-see-welcome-message-on-connection
    (with-mock-io (channel-connected mock-channel {:address "127.0.0.1"}))
    (is (<= 1 (count (mock-channel @notifications))))))

(testing "Player Disconnection"
  (deftest player-is-made-asleep-on-disconnect
    (with-mock-io
      (make-room "The Hall" "The hall is long")
      (make-player "bob" 1)
      (channel-connected mock-channel {:address "127.0.0.8"})
      (is (false? (:awake @(find-player-by-name "bob"))))
      (connect-handler mock-channel "bob")
      (is (:awake @(find-player-by-name "bob")))
      (channel-disconnected mock-channel {:address "127.0.0.8"})
      (is (false? (:awake @(find-player-by-name "bob")))))))
