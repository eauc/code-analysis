(ns metrics.core
  {:nextjournal.clerk/visibility {:code :hide :result :show}})

(defn ->metric
  [metric data]
  (->> data
       (map metric)
       (reduce +)))

(defn ->metric-by
  [metric by data]
  (-> (group-by by data)
      (update-vals
       (fn [data] (->metric metric data)))))

(defn ->time-serie
  [->value data]
  (->> data
       (sort-by :date)
       (partition-by :date)
       (map (fn [data]
              [(-> data first :date str)
               (->value data)]))))

(defn cumulative-sum
  [metrics-serie]
  (let [x (map first metrics-serie)
        y (map second metrics-serie)
        multi? (-> y first map?)
        add (if multi? #(merge-with + %1 %2) +)]
    (map vector x (reductions add y))))
