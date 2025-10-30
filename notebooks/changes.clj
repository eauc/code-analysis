(ns changes
  {:nextjournal.clerk/visibility {:code :hide :result :show}}
  (:require
   [clojure.string]
   [config :as cfg]
   [data.file-stats]
   [data.log]
   [files.deltas :refer [deltas-join-commits filter-since]]
   [files.modules :refer [file-nodes-with-module-config]]
   [files.tree :refer [files->nodes filter-max-depth]]
   [graphs.trees :refer [tree-plot]]
   [metrics.changes :refer [file-nodes-with-changes]]
   [metrics.core :refer [metric->color top-files-list metric->str]]
   [metrics.complexity :refer [file-nodes-with-complexity filter-min-complexity complexity->tree-plot-value]]
   [nextjournal.clerk :as clerk]))

; # Changes

; Project

(def project-name
  "georges-lib")

; Config

; ^::clerk/no-cache
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
(def commits
  (->> (:commits log)
       (map #(vector (:hash %) %))
       (into {})))

; ^{::clerk/visibility {:result :hide}}
(def file-deltas
  (-> (:file-deltas log)
      (update-vals #(deltas-join-commits [:date] commits %))))

^{::clerk/visibility {:result :hide}}
(def metrics
  [["Relative Σchurn" #(-> % :changes :churn double (/ (max 1 (-> % :complexity :lines))))]])

^{::clerk/visibility {:result :hide}}
(def base-nodes
  (->> files
       (files->nodes project-name)
       (file-nodes-with-module-config (config :modules))
       (filter-max-depth (config :max-depth))
       (file-nodes-with-complexity file-stats)
       (filter-min-complexity :lines (config :min-complexity))
       (file-nodes-with-changes file-deltas)))

(->> metrics
     (mapcat
      (fn [[title metric]]
        (let [nodes (mapv #(assoc % :metric (metric %)) base-nodes)]
          [(clerk/html
            [:div
             [:h3 title]
             [:p "File with max changes:"]
             (top-files-list :metric nodes)])
           (tree-plot
            {:nodes nodes
             :id :path
             :label (fn [{:keys [depth metric path]}]
                      (str depth " - " path "<br />" (metric->str metric)))
             :color (metric->color :metric nodes)
             :value (complexity->tree-plot-value nodes)
             :max-depth -1})]))))
