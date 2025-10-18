(ns metrics.complexity
  {:nextjournal.clerk/visibility {:code :hide :result :show}}
  (:require [clojure.string]
            [files.tree :refer [child-nodes]]
            [metrics.core :refer [->metric]]))

(defn file-nodes-with-complexity
  [files-stats file-nodes]
  (map
   (fn  [{:keys [leaves] :as node}]
     (let [complexity (->> leaves
                           (mapv (comp :complexity files-stats))
                           (apply merge-with +))]
       (assoc node :complexity complexity)))
   file-nodes))

(defn filter-min-complexity
  [complexity min-complexity file-nodes]
  (->> file-nodes
       (filterv #(<= (-> % :module :min-complexity (or min-complexity) (or 0))
                    (-> % :complexity complexity)))))

(defn complexity->tree-plot-value
  [nodes]
  (fn [{:keys [path complexity]}]
    (- (complexity :lines)
       (->> (child-nodes path nodes)
            (->metric (comp :lines :complexity))))))
