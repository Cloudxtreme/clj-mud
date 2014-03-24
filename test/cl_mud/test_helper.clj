(ns cl-mud.test-helper
  (:require [clojure.test :refer :all]
            [cl-mud.core :refer :all]
            [cl-mud.world :refer :all]
            [cl-mud.rooms :refer :all]))

;; Steal the default config to re-set it
(def default-config @cl-mud.core/config)

(defn reset-global-state []
  (compare-and-set! next-id @next-id 0)
  (compare-and-set! rooms @rooms #{})
  (compare-and-set! current-room @current-room nil)
  (compare-and-set! exits @exits #{})
  (compare-and-set! players @players #{})
  (compare-and-set! command-handlers @command-handlers {})
  (compare-and-set! config @config default-config))
