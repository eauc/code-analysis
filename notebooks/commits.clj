(ns commits
  {:nextjournal.clerk/visibility {:code :hide :result :show}}
  (:require
   [clojure.pprint :as pprint]
   [clojure.string]
   [config :as cfg]
   [data.file-stats]
   [data.log]
   [files.modules :refer [file-nodes-with-module-config]]
   [files.tree :refer [files->nodes filter-max-depth]]
   [graphs.pies :refer [pie]]
   [graphs.plots :refer [plots]]
   [graphs.trees :refer [tree-plot]]
   [metrics.commits.types :refer [commit-type->color commits-with-type ordered-commit-types file-nodes-with-commit-type-edits]]
   [metrics.commits.words :refer [commit-words commit-func-words]]
   [metrics.complexity :refer [file-nodes-with-complexity filter-min-complexity complexity->tree-plot-value]]
   [metrics.core :refer [->metric-by ->time-serie cumulative-sum metric->color top-files-list]]
   [nextjournal.clerk :as clerk]
   [tick.core :as t]))

; # Commits

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
(def file-deltas
  (:file-deltas log))

^{::clerk/visibility {:result :hide}}
(def commits
  (->> (:commits log)
       commits-with-type))

#_(->> commits
       (filter (fn [{:keys [type]}] (= type :unknown)))
       (map :description))

; ## Commit types

^{::clerk/visibility {:result :hide}}
(def metrics
  [[(constantly 1) "#commits"]
   [:edits "Edits"]])

(doall
 (for [[metric name] metrics]
   [(pie
     {:data (->metric-by metric :type commits)
      :order ordered-commit-types
      :colors commit-type->color
      :title (str name " / type")})
    (plots
     {:data (->> commits
                 (->time-serie
                  #(->metric-by metric :type %))
                 cumulative-sum)
      :series (mapv #(vector % % (commit-type->color %)) (remove #{:unknown} ordered-commit-types))
      :title (str name " / type over time")
      :stacked? true})]))

; ## Fixes hotspots

^{::clerk/visibility {:result :hide}}
(def metrics
  [["Relative edits" #(/ (-> % :type->edits :fix (or 0) double) (-> % :complexity :lines (max 1)))]])

^{::clerk/visibility {:result :hide}}
(def hash->commit
  (->> commits
       (map #(vector (:hash %) %))
       (into {})))

^{::clerk/visibility {:result :hide}}
(def nodes
  (->> files
       (files->nodes project-name)
       (file-nodes-with-module-config (config :modules))
       (filter-max-depth (config :max-depth))
       (file-nodes-with-complexity file-stats)
       (filter-min-complexity :lines (config :min-complexity))
       (file-nodes-with-commit-type-edits hash->commit file-deltas)))

(doall
 (->> metrics
      (mapcat
       (fn [[title metric]]
         (let [nodes (mapv #(assoc % :metric (metric %)) nodes)]
           [(clerk/html
             [:div
              [:h3 title]
              [:p "Top bug hotspot:"]
              (top-files-list :metric nodes)])
            (tree-plot
             {:nodes nodes
              :id :path
              :label (fn [{:keys [depth metric path]}]
                       (str depth " - " path "<br />Fixes: "
                            (pprint/cl-format nil  "~,2f" metric)))
              :color (metric->color :metric nodes)
              :value (complexity->tree-plot-value nodes)
              :max-depth -1})])))))

; ## Descriptions

; All words from commit descriptions

(tree-plot
 {:nodes (->> commits
              commit-words
              (->metric-by (constantly 1) clojure.string/lower-case)
              (sort-by second)
              reverse
              (take 30)
              (mapv (fn [[word count]] {:id word :parent "word-map" :value count})))})

; Functional words from commit descriptions

(tree-plot
 {:nodes (->> commits
              commit-func-words
              (->metric-by (constantly 1) clojure.string/lower-case)
              (sort-by second)
              reverse
              (take 30)
              (mapv (fn [[word count]] {:id word :parent "word-map" :value count})))})

; TODO commits size analysis / edits added deleted churn
; cum over time, distribution
; by authors
