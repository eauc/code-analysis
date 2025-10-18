(ns graphs.plots
  {:nextjournal.clerk/visibility {:code :hide :result :show}}
  (:require [nextjournal.clerk :as clerk]))

(defn plot-data
  [data [serie name color]]
  (merge {:type :scatter
          :mode :line
          :name name
          :x (mapv first data)
          :y (mapv #(-> % second (get serie 0)) data)}
         (when color
           {:marker {:color color}})))

(defn plots
  [{:keys [data series stacked? title]}]
  (clerk/plotly
   {:data (cond->> (mapv #(plot-data data %) series)
            stacked? (mapv #(merge % {:stackgroup :one})))
    :layout {:title {:text title}}
    :config {:displayModeBar false}}))
