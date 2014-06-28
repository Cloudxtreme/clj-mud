(ns clj-mud.core-test
  (:require [clojure.test :refer :all]
            [clj-mud.core :refer :all]
            [clj-mud.room :refer :all]
            [clj-mud.world :refer :all]
            [clj-mud.test-helper :as test-helper]
            [lamina.core :refer :all]
            [aleph.tcp :refer :all]
            [gloss.core :refer :all]))

(def last-mock-arg (atom nil))
(def notifications (atom {}))

;; Mock handler
(defn mock-handler
  [ch args]
  (compare-and-set! last-mock-arg @last-mock-arg (first args)))

(def mock-channel :mock-channel)

(defn mock-enqueue
  "Mock implementation of 'enqueue' that appends to notifications list of the specified channel"
  [ch arg]
  (if (nil? (ch @notifications))
    (swap! notifications assoc ch [arg])
    (swap! notifications assoc ch (conj (ch @notifications) arg))))

(defn mock-println
  "Mock implementation of 'println' that does nothing"
  [arg])

(defmacro with-mock-io
  "Convenience macro to help capture println output."
  [& args]
  `(with-redefs-fn {#'enqueue mock-enqueue #'println mock-println} (fn [] ~@args)))

(use-fixtures :each
  (fn [f]
    (test-helper/reset-global-state)
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
    (with-mock-io (dispatch-command mock-channel "say Hello, world!"))
    (is (= "You say, \"Hello!\"") (last (mock-channel @notifications)))))

(testing "Handlers"
  (deftest connect-handler-test
    ;; Can't connect without a room to connect in
    (make-room "The Hall" "The hall is long")
    (is (= 0 (count @players)))
    (with-mock-io (connect-handler mock-channel "bob"))
    (is (= 1 (count @players)))
    (is (= "bob" (:name @(last @players)))))
  
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
    (let [den (make-room "The Den" "This is a nice den")
          hall (make-room "The Hall" "A Long Hallway")]
      (make-exit den hall "east")
      (make-exit hall den "west")
      (move-to den)
      (with-mock-io (look-handler mock-channel))
      (is (= ["The Den"
              ""
              "This is a nice den"
              ""
              "    Exits: east"] (mock-channel @notifications)))))

  (deftest walk-handler-moves-the-player
    (let [den (make-room "The Den" "This is a nice den")
          hall (make-room "The Hall" "A Long Hallway")]
      (make-exit den hall "east")
      (make-exit hall den "west")
      (move-to den)
      (is (= den @current-room))
      (with-mock-io (walk-handler mock-channel "east"))
      (is (= hall @current-room))))

  (deftest walk-handler-notifies-of-incorrect-usage
    (with-mock-io (walk-handler mock-channel nil))
    (is (= "Go where?" (last (get @notifications mock-channel)))))
  
  (deftest walk-handler-wont-move-to-nonexistent-exit
    (let [den (make-room "The Den" "This is a nice den")
          hall (make-room "The Hall" "A Long Hallway")]
      (make-exit den hall "east")
      (make-exit hall den "west")
      (move-to den)
      (is (= den @current-room))
      (with-mock-io (walk-handler mock-channel "north"))
      (is (= den @current-room))
      (is (= "There's no exit in that direction!" (last (mock-channel @notifications))))))

  (deftest walk-handler-looks-at-new-room
    (let [den (make-room "The Den" "This is a nice den")
          hall (make-room "The Hall" "A Long Hallway")]
      (make-exit den hall "east")
      (make-exit hall den "west")
      (move-to den)
      (with-mock-io (walk-handler mock-channel "east"))
      (is (= ["The Hall"
              ""
              "A Long Hallway"
              ""
              "    Exits: west"] (mock-channel @notifications)))))

  (deftest help-handler-returns-at-least-one-line
    "We don't really care what it returns, just that it returns something."
    (with-mock-io (help-handler mock-channel))
    (is (<= 1 (count (mock-channel @notifications))))))

(testing "Connecting Channels"
  (deftest channels-are-added-to-channel-set-on-connection
    (with-mock-io (channel-connected mock-channel {:address "0:0:0:0:0:0:0:1%0"}))
    (is (= {mock-channel {:address "0:0:0:0:0:0:0:1%0"}} @client-channels)))

  (deftest channels-are-removed-from-channel-set-on-disconnect
    (with-mock-io (channel-connected mock-channel {:address "0:0:0:0:0:0:0:1%0"}))
    (is (not (empty? @client-channels)))
    (with-mock-io (channel-disconnected mock-channel {:address "0:0:0:0:0:0:0:1%0"}))
    (is (empty? @client-channels))))

(testing "Player Connection"
  (deftest players-see-welcome-message-on-connection
    (with-mock-io (channel-connected mock-channel {:address "127.0.0.1"}))
    (is (<= 1 (count (mock-channel @notifications))))))
