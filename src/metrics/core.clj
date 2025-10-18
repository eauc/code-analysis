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

(defn red->green
  [s]
  [s, (- 1.0 s), 0])

(defn blue->red
  [s]
  [(+ 0.2 (* 0.45 (- 1.0 s))),
   (- 1.0 (abs (* 2.0 (- 0.5 s)))),
   (+ 0.15 (* 0.45 s))])

(defn metric->color
  ([metric data s->rgb]
   (let [v-range (->> data (mapv (comp sqrt metric)) (remove nil?))
         v-min (apply min v-range)
         v-max (apply max v-range)]
     (fn [item]
       (let [v (-> item metric sqrt (or v-min))
             s (/ (double (- v v-min)) (- v-max v-min))
             [r g b] (s->rgb s)]
         (str "rgb("
              (int (* 255 r))
              " , "
              (int (* 255 g))
              ", "
              (int (* 255 b))
              ")")))))
  ([metric data]
   (metric->color metric data red->green)))

(defn metric->str
  [value]
  (pprint/cl-format nil "~,2f" value))

(defn top-files-list
  [metric file-nodes]
  (let [parents (->> file-nodes (map :parent) set)
        top-files (->> file-nodes
                       (remove #(parents (:path %)))
                       (sort-by #(- (metric %)))
                       (take 10)
                       (mapv (juxt :path metric)))]
    [:ul
     (map
      (fn [[path value]]
        [:li (str path " (" (metric->str value) ")")])
      top-files)]))
