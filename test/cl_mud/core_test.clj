(ns cl-mud.core-test
  (:require [clojure.test :refer :all]
            [cl-mud.core :refer :all]
            [cl-mud.rooms :refer :all]
            [cl-mud.test-helper :as test-helper]))

(def last-mock-arg (atom nil))
(def notifications (atom []))

;; Mock handler
(defn mock-handler
  [args]
  (compare-and-set! last-mock-arg @last-mock-arg (first args)))

(defn mock-println
  "Mock implementation of 'println' that appends to notifications list"
  [& arg]
  (if arg (swap! notifications conj (first arg))))

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
    (is (= '(:say "This is super fun, too!") (parse-command "say This is super fun, too!"))))

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


(testing "Handlers"
  (deftest look-handler-non-existent-rooms
    (with-redefs-fn {#'println mock-println}
      #(look-handler nil))
    (is (= ["You don't see that here"] @notifications)))

  (deftest look-handler-prints-current-room
    (let [den (make-room "The Den" "This is a nice den")
          hall (make-room "The Hall" "A Long Hallway")]
      (make-exit den hall "east")
      (make-exit hall den "west")
      (move-to den)
      (with-redefs-fn {#'println mock-println} #(look-handler nil))
      (is (= ["The Den"
              "This is a nice den"
              "    Exits: east"] @notifications)))))
