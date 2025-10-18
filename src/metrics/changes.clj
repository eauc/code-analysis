(ns metrics.changes
  {:nextjournal.clerk/visibility {:code :hide :result :show}}
  (:require [clojure.string]
            [metrics.core :refer [->metric]]))

(def change-metrics
  [[:count (constantly 1)]
   [:edits :edits]
   [:added :added]
   [:deleted :deleted]
   [:churn :churn]])

(defn deltas->change-stats
  [deltas]
  (let [deltas (->> deltas
                    (group-by :hash)
                    (mapv (comp first second)))]
    (->> change-metrics
         (mapv (fn [[k metric]]
                [k (->metric metric deltas)]))
         (into {}))))

(defn file-nodes-with-changes
  [file-deltas file-nodes]
  (->> file-nodes
       (mapv (fn [{:keys [leaves] :as node}]
              (let [change-stats (->> leaves
                                      (mapcat file-deltas)
                                      deltas->change-stats)]
                (assoc node :changes change-stats))))))
