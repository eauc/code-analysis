(ns age
  {:nextjournal.clerk/visibility {:code :hide :result :show}}
  (:require
   [clojure.math :refer [sqrt]]
   [clojure.string]
   [config :as cfg]
   [data.file-stats]
   [data.log]
   [files.modules :refer [file-nodes-with-module-config]]
   [files.tree :refer [files->nodes filter-max-depth]]
   [graphs.bars :refer [v-bars]]
   [graphs.trees :refer [tree-plot]]
   [metrics.age :refer [dates->age-stats file-nodes-with-age-stats]]
   [metrics.core :refer [->metric blue->red red->green metric->color top-files-list]]
   [metrics.complexity :refer [file-nodes-with-complexity filter-min-complexity complexity->tree-plot-value]]
   [nextjournal.clerk :as clerk]
   [tick.core :as t]))

; # Age

; Project

(def project-name
  "georges-lib")

; Config

; ^::clerk/no-cache
(def config
  (cfg/read! project-name))

; ### Files stats data

(def file-stats
  (data.file-stats/read! config))

^{::clerk/visibility {:result :hide}}
(def files
  (keys file-stats))

; ## Age distribution

^{::clerk/visibility {:result :hide}}
(def line-ages
  (->> (vals file-stats)
       (mapv :dates)
       (apply merge-with +)))

(v-bars
 {:data (->> line-ages
             (group-by (fn [[date _]] (t/between (t/date date) (config :stop-time) :months)))
             (mapv (fn [[months date-ns]] [months (->metric second date-ns)]))
             (filterv (fn [[months _]] (< 0 months)))
             (into {}))
  :title "Lines age"})

^{::clerk/visibility {:result :hide}}
(def file-age-stats
  (-> file-stats
      (update-vals (comp dates->age-stats :dates))))

^{::clerk/visibility {:result :hide}}
(def metrics
  [["last modification" #(t/between (:newest %) (config :stop-time) :months)]
   ["lines age p90" #(t/between (:p90 %) (config :stop-time) :months)]
   ["modification range" #(t/between (:oldest %) (:newest %) :months)]])

(->> metrics
     (map
      (fn [[title metric]]
        (v-bars
         {:data (->> file-age-stats
                     vals
                     (remove nil?)
                     (mapv metric)
                     (filterv #(< 0 %))
                     (frequencies))
          :title (str "Files / " title)}))))

; ## Age maps

^{::clerk/visibility {:result :hide}}
(def base-nodes
  (->> files
       (files->nodes project-name)
       (file-nodes-with-module-config (config :modules))
       (filter-max-depth (config :max-depth))
       (file-nodes-with-complexity file-stats)
       (filter-min-complexity :lines (config :min-complexity))
       (file-nodes-with-age-stats file-stats)))

^{::clerk/visibility {:result :hide}}
(def metrics
  [["Last modification"
    #(t/between (-> % :age :newest) (config :stop-time) :days)
    #(str (t/<< (config :stop-time) (t/of-days %)) " (" % " days)")
    blue->red]
   ["p90 age"
    #(t/between (-> % :age :p90) (config :stop-time) :days)
    #(str (t/<< (config :stop-time) (t/of-days %)) " (" % " days)")
    blue->red]
   ; ["Median age"
   ;  #(t/between (-> % :age :median) (config :stop-time) :days)
   ;  #(str (t/<< (config :stop-time) (t/of-days %)) " (" % " days)")
   ;  blue->red]
   ["Creation date"
    #(t/between (-> % :age :oldest) (config :stop-time) :days)
    #(str (t/<< (config :stop-time) (t/of-days %)) " (" % " days)")
    blue->red]
   ["Modification range"
    #(t/between (-> % :age :oldest) (-> % :age :newest) :days)
    #(str % " days")
    red->green]])

(->> metrics
     (mapcat
      (fn [[title metric metric->str color-scale]]
        (let [nodes (mapv #(assoc % :metric (metric %)) base-nodes)]
          [(clerk/html
            [:div
             [:h3 title]
             [:p "Top 10 files:"]
             [:ul
              (top-files-list :metric nodes)]])
           (tree-plot
            {:nodes nodes
             :id :path
             :label (fn [{:keys [depth path metric]}]
                      (str depth " - " path "<br /> " (metric->str metric)))
             :color (metric->color (comp sqrt :metric) nodes color-scale)
             :value (complexity->tree-plot-value nodes)
             :max-depth -1})]))))
