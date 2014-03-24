(ns cl-mud.core-test
  (:require [clojure.test :refer :all]
            [cl-mud.core :refer :all]
            [cl-mud.test-helper :as test-helper]))

(def last-mock-arg (atom nil))

;; Mock handler
(defn mock-handler
  [args]
  (compare-and-set! last-mock-arg @last-mock-arg (first args)))

(use-fixtures :each
  (fn [f]
    (test-helper/reset-global-state)
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
