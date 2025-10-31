(ns metrics.bus-factor
  {:nextjournal.clerk/visibility {:code :hide :result :show}}
  (:require
   [clojure.string]
   [metrics.core :refer [rgb->str]]))

(defn- authors->bus-factor
  [lines-total authors]
  (->> authors
       (mapv #(double (/ (* (inc (* %1 2)) (second %2)) lines-total)) (range))
       (reduce +)))

(defn- files-bus-factor
  [file-stats files]
  (let [authors (->> files
                     (mapv (comp :authors file-stats))
                     (apply merge-with +)
                     (sort-by #(- (second %))))
        lines-total (->> files
                         (mapv (comp :lines :complexity file-stats))
                         (reduce +))]
    (authors->bus-factor lines-total authors)))

(defn file-nodes-with-bus-factor
  [file-stats file-nodes]
  (->> file-nodes
       (mapv (fn [{:keys [leaves] :as node}]
               (assoc node :bus-factor (files-bus-factor file-stats leaves))))))

(def bus-factor->color
  (let [good-low 3
        good-high 5
        bad-high 10]
    (fn [{:keys [bus-factor]}]
      (let [[s v] (cond
                    (> good-low bus-factor) [(/ (- bus-factor 1) (- good-low 1)) 1.]
                    (> good-high bus-factor) [1. 1.]
                    (> bad-high bus-factor) [1. (/ (- bad-high bus-factor) (- bad-high good-high))]
                    :else [1. 0.])]
        (rgb->str (- 1 v) v (- 1 s))))))
