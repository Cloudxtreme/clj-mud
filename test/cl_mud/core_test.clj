(ns cl-mud.core-test
  (:require [clojure.test :refer :all]
            [cl-mud.core :refer :all]))

;; Mock handler that just returns
(def last-said-string (atom nil))

(defn mock-say-handler
  [args]
  (compare-and-set! last-said-string @last-said-string (first args)))

(defn reset-command-handlers
  [f]
  (compare-and-set! command-handlers @command-handlers {})
  (f))

(use-fixtures :each reset-command-handlers)

(testing "Parsing Commands"
  (deftest test-parse-command-should-split-string-into-list
    (is (= '(:hello "world") (parse-command "hello world")))
    (is (= '(:look "north") (parse-command "look north")))
    (is (= '(:inventory) (parse-command "inventory")))
    (is (= '(:say "This is super fun!") (parse-command "say This is super fun!")))))

(testing "Registering Commands"
  (deftest test-registering-a-command-should-append-to-command-handlers-atom
    (is (= {} @command-handlers))
    (register-command :say mock-say-handler)
    (is (= {:say mock-say-handler} @command-handlers))))
