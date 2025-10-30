(ns coupling
  {:nextjournal.clerk/visibility {:code :hide :result :show}}
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string]
            [config :as cfg]
            [data.file-stats]
            [data.log]
            [files.modules :refer [->modules file-nodes-with-module-config files->module-paths]]
            [files.tree :refer [files->nodes filter-max-depth]]
            [graphs.tree-deps :refer [tree-deps-plot]]
            [graphs.trees :refer [tree-plot]]
            [metrics.complexity :refer [file-nodes-with-complexity filter-min-complexity complexity->tree-plot-value]]
            [metrics.core :refer [metric->color metric->str top-files-list]]
            [metrics.coupling :refer [->coupling-factors coupling-factors->deps ->coupling-tree file-nodes-with-coupling-scores]]
            [nextjournal.clerk :as clerk]
            [tick.core :as t]))

; # Coupling

; Project

(def project-name
  "georges-lib")

; config

(def config
  (cfg/read! project-name))

; ### Commits log data
(def log
  (data.log/read! config))

; ### Files stats data
(def file-stats
  (data.file-stats/read! config))

; ### Files

^{::clerk/visibility {:result :hide}}
(def files
  (keys file-stats))

^{::clerk/visibility {:result :hide}}
(def file-deltas
  (:file-deltas log))

^{::clerk/visibility {:result :hide}}
(def module-paths
  (files->module-paths (config :modules) files))

^{::clerk/visibility {:result :hide}}
(def coupling-factors
   (->coupling-factors file-deltas module-paths))

^{::clerk/visibility {:result :hide}}
(def coupling-deps
  (coupling-factors->deps coupling-factors))

^{::clerk/visibility {:result :hide}}
(def coupling-tree
  (->coupling-tree files module-paths))

; ## Top couplings

(clerk/table
 (clerk/use-headers
  (concat [["from" "to" "factor"]]
          (->> coupling-deps
               (take 20)
               (map (fn [{:keys [source target value]}]
                      [source target value]))
               (sort-by (juxt #(- (nth % 2)) first second))))))

; ## Module couplings

(tree-deps-plot
 {:data {:tree coupling-tree
         :deps coupling-deps}
  :id :path
  :label :path
  :width 700
  :height 700})

; ## Coupling hotspot map

^{::clerk/visibility {:result :hide}}
(def metric
  #(/ (:couplings %) (-> % :complexity :lines)))

^{::clerk/visibility {:result :hide}}
(def nodes
  (->> files
       (files->nodes project-name)
       (file-nodes-with-module-config (config :modules))
       (filter-max-depth (config :max-depth))
       (file-nodes-with-complexity file-stats)
       (filter-min-complexity :lines (config :min-complexity))
       (file-nodes-with-coupling-scores file-deltas)
       (map #(assoc % :metric (metric %)))))

(clerk/html
 [:div
  [:p "Top coupled files:"]
  (top-files-list :metric nodes)])

(tree-plot
 {:nodes nodes
  :id :path
  :label (fn [{:keys [depth metric path]}]
           (str depth " - " path "<br />Fixes: "
                (metric->str metric)))
  :color (metric->color :metric nodes)
  :value (complexity->tree-plot-value nodes)
  :max-depth -1})
