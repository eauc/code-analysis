(ns graphs.pies
  {:nextjournal.clerk/visibility {:code :hide :result :show}}
  (:require [clojure.string]
            [nextjournal.clerk :as clerk]))

(defn pie-data
  [{:keys [data order colors]}]
  (let [order (or order (keys data))
        values (mapv data order)]
    (merge {:type :pie
            :values values
            :labels order
            :sort (not order)}
           (when colors
             {:marker {:colors (mapv colors order)}}))))

(defn pie
  [{:keys [title]
    :as options}]
  (clerk/plotly
   {:data [(pie-data options)]
    :layout {:title {:text title}
             :margin {:t 50 :b 0 :l 0 :r 0}}}))
