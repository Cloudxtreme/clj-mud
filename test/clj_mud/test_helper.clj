(ns clj-mud.test-helper
  (:require [clojure.test :refer :all]
            [clj-mud.core :refer :all]
            [clj-mud.world :refer :all]
            [clj-mud.room :refer :all]
            [lamina.core :refer :all]
            [aleph.tcp :refer :all]
            [gloss.core :refer :all]))

;; Steal the default config to re-set it
(def default-config @clj-mud.world/config)

(defn reset-global-state []
  (compare-and-set! next-id @next-id 0)
  (compare-and-set! rooms @rooms #{})
  (compare-and-set! exits @exits #{})
  (compare-and-set! players @players #{})
  (compare-and-set! command-handlers @command-handlers {})
  (compare-and-set! client-channels @client-channels {})
  (compare-and-set! config @config default-config))

(defmacro with-mock-io
  "Convenience macro to help capture println output."
  [& body]
  ;; `(with-redefs-fn {#'enqueue mock-enqueue #'println mock-println} (fn [] (do ~@body)))
  `(with-redefs-fn {#'enqueue mock-enqueue} (fn [] (do ~@body))))

(def mock-channel :mock-channel)
(def last-mock-arg (atom nil))
(def notifications (atom {}))

(defn mock-println
  "Mock implementation of 'println' that does nothing"
  [& args])

(defn mock-enqueue
  "Mock implementation of 'enqueue' that appends to notifications list of the specified channel"
  [ch arg]
  (if (nil? (ch @notifications))
    (swap! notifications assoc ch [arg])
    (swap! notifications assoc ch (conj (ch @notifications) arg))))
