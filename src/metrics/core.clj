(ns metrics.core
  {:nextjournal.clerk/visibility {:code :hide :result :show}}
  (:require [clojure.math :refer [sqrt]]
            [clojure.pprint :as pprint]))

(defn ->metric
  [metric data]
  (->> data
       (mapv metric)
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
       (mapv (fn [data]
              [(-> data first :date str)
               (->value data)]))))

(defn cumulative-sum
  [metrics-serie]
  (let [x (mapv first metrics-serie)
        y (mapv second metrics-serie)
        multi? (-> y first map?)
        add (if multi? #(merge-with + %1 %2) +)]
    (mapv vector x (reductions add y))))

(defn metric->color
  [metric data]
  (let [v-range (->> data (mapv (comp sqrt metric)) (remove nil?))
        v-min (apply min v-range)
        v-max (apply max v-range)]
    (fn [item]
      (let [v (-> item metric sqrt (or v-min))
            s (/ (double (- v v-min)) (- v-max v-min))]
        (str "rgb(" (int (* 255 s)) " , " (int (* 255 (- 1 s))) ", 0)")))))

(defn metric->str
  [value]
  (pprint/cl-format nil "~,2f" value))

(defn top-files-list
  [metric file-nodes]
  (let [top-files (->> file-nodes
                       (sort-by #(- (metric %)))
                       (take 10)
                       (mapv (juxt :path metric)))]
    [:ul
     (map
      (fn [[path value]]
        [:li (str path " (" (metric->str value) ")")])
      top-files)]))
