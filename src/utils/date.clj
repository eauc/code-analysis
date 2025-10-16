(ns utils.date
  {:nextjournal.clerk/visibility {:code :hide :result :show}}
  (:require [tick.core :as t]))

(defn min-date
  ([] nil)
  ([a b]
   (if (t/< a b) a b)))

(defn max-date
  ([] nil)
  ([a b]
   (if (t/< a b) b a)))
