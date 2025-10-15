(ns utils.core)

(defn sum-by
  [by coll]
  (->> coll (map by) (apply +)))

(defn count-by
  [by coll]
  (-> (group-by by coll)
      (update-vals count)))
