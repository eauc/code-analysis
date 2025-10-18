(ns metrics.age
  (:require [tick.core :as t]))

(defn dates->age-stats
  [dates]
  (let [dates (->> dates keys sort)]
    (if (seq dates)
      {:oldest (-> dates first t/date)
       :median (-> dates (nth (/ (count dates) 2)) t/date)
       :p90 (-> dates (nth (int (* 0.9 (count dates)))) t/date)
       :newest (-> dates last t/date)}
      nil)))

(defn file-stats->age-stats
  [file-stats]
  (let [dates (->> file-stats
                   (mapv :dates)
                   (apply merge-with +))]
    (dates->age-stats dates)))

(defn file-nodes-with-age-stats
  [file-stats file-nodes]
  (->> file-nodes
       (mapv (fn [{:keys [leaves] :as node}]
               (assoc node :age (->> leaves
                                     (mapv file-stats)
                                     file-stats->age-stats))))))
