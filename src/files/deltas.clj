(ns files.deltas
  {:nextjournal.clerk/visibility {:code :hide :result :show}}
  (:require [clojure.string]
            [tick.core :as t]))

(defn deltas-join-commits
  [select commits deltas]
  (->> deltas
       (mapv (fn [{:keys [hash] :as delta}]
              (let [commit (get commits hash)]
                (merge delta (select-keys commit select)))))))

(defn filter-since
  [since deltas]
  (->> deltas
       (filterv #(t/< since (:date %)))))
