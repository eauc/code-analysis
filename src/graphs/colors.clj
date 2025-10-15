(ns graphs.colors
  {:nextjournal.clerk/visibility {:code :hide :result :show}}
  (:require [clojure.string]))

(def default-colors
  ["#1f77b4" "#ff7f0e" "#2ca02c" "#d62728" "#9467bd" "#8c564b" "#e377c2" "#7f7f7f" "#bcbd22" "#17becf"])

(defn colors-for
  [coll]
  (let [value->color
        (->> coll
             (map (fn [color value] [value color]) (cycle default-colors))
             (into {}))]
    #(get value->color % "grey")))
