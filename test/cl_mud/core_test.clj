(ns cl-mud.core-test
  (:require [clojure.test :refer :all]
            [cl-mud.core :refer :all]))

(def last-mock-arg (atom nil))

;; Mock handler
(defn mock-handler
  [args]
  (compare-and-set! last-mock-arg @last-mock-arg (first args)))

(defn reset-command-handlers
  [f]
  (compare-and-set! command-handlers @command-handlers {})
  (f))

(use-fixtures :each reset-command-handlers)

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
