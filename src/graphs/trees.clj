(ns graphs.trees
  {:nextjournal.clerk/visibility {:code :hide :result :show}}
  (:require [clojure.string]
            [nextjournal.clerk :as clerk]))

(defn tree-data
  [nodes {id-k :id
          value-k :value
          label-k :label
          color-k :color
          :keys [type max-depth]
          :or {type :treemap
               max-depth 3
               id-k :id
               value-k :value
               label-k :id
               color-k :color}}]
  (let [parents (mapv :parent nodes)
        ids (mapv id-k nodes)
        values (mapv value-k nodes)
        labels (mapv label-k nodes)
        colors (mapv color-k nodes)]
    {:type type
     :parents parents
     :ids ids
     :labels labels
     :values values
     :marker {:colors colors}
     :maxdepth max-depth}))

(defn tree-plot
  [{:keys [nodes] :as opts}]
  (let [data (tree-data nodes opts)]
    (clerk/plotly
     {:data [data]
      :layout {:margin {:b 0 :t 0 :l 0 :r 0}}
      :config {:displayModeBar false}})))
