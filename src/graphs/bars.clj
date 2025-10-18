(ns graphs.bars
  (:require [nextjournal.clerk :as clerk]))

(defn bars-data
  [data names [values name options]]
  (merge
   options
   {:type :bar
    :name name
    :x (mapv values data)
    :y (mapv names data)}))

(defn h-bars
  [{:keys [title data names series]}]
  (clerk/plotly
   {:data (mapv
           #(-> (bars-data data names %)
                (merge {:orientation :h}))
           series)
    :layout {:title {:text title}
             :yaxis {:automargin true}}
    :config {:displayModeBar false}}))

(defn v-bars
  [{:keys [title data]}]
  (let [x (keys data)]
   (clerk/plotly
    {:data [{:x x
             :y (map data x)
             :type :bar}]
     :layout {:title {:text title}}
     :config {:displayModeBar false}})))
