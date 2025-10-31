(ns file-xrays
  {:nextjournal.clerk/visibility {:code :hide :result :show}}
  (:require
   [config :as cfg]
   [data.file-stats]
   [data.log :refer [project-commits]]
   [graphs.bars :refer [v-bars]]
   [graphs.pies :refer [pie]]
   [graphs.plots :refer [plots]]
   [graphs.trees :refer [tree-plot]]
   [graphs.tree-deps :refer [tree-deps-plot]]
   [metrics.age :refer [->line-ages]]
   [metrics.authors :refer [->authors-stats ->file-authors]]
   [metrics.commits.types :refer [commits-with-type commit-type->color ordered-commit-types]]
   [metrics.commits.words :refer [->func-word-frequencies]]
   [metrics.core :refer [->metric-by ->time-serie cumulative-sum]]
   [metrics.coupling :refer [->single-coupling-factors coupling-factors->deps ->coupling-tree]]
   [nextjournal.clerk :as clerk]))

; # File X-Rays

; Project

(def project-name
  "tree-sitter")

; File

(def file-path
  "lib/src/query.c")

; Config

^::clerk/no-cache
(def config
  (cfg/read! project-name))

; ### Commits log data

(def log
  (data.log/read! config))

; ### File stats data

(def file-stats
  (-> (data.file-stats/read! config)
      (select-keys [file-path])))

^{::clerk/visibility {:result :hide}}
(def file-deltas
  (-> (:file-deltas log)
      (select-keys [file-path])))

^{::clerk/visibility {:result :hide}}
(def commits
  (->> (:commits log)
       (project-commits file-deltas)
       commits-with-type))

; ## Authors

^{::clerk/visibility {:result :hide}}
(def authors
  (:authors log))

^{::clerk/visibility {:result :hide}}
(def authors-stats
  (->authors-stats authors commits))

^{::clerk/visibility {:result :hide}}
(def file-authors
  (->file-authors authors file-stats))

(pie
 {:data (into {} file-authors)
  :order (mapv first file-authors)})

(doall
 (for [[metric name] [[(constantly 1) "#commits"]
                      [:diff "diff"]
                      [:churn "churn"]]]
   (let [metrics (->> commits
                      (->time-serie #(->metric-by metric :author %))
                      (cumulative-sum))]
     (plots
      {:data metrics
       :series (mapv (juxt :email :author) authors-stats)
       :title (str "Cumulative " name " over time")
       :stacked? true}))))

; ## Age

^{::clerk/visibility {:result :hide}}
(def line-ages
  (->line-ages config file-stats))

(v-bars
 {:data line-ages
  :title "Lines age"})

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

; ## Commits descriptions

; Functional words from commit descriptions

(tree-plot
 {:nodes (->func-word-frequencies commits)})

; ## Couplings

^{::clerk/visibility {:result :hide}}
(def coupling-factors
  (->single-coupling-factors commits (:file-deltas log) file-path))

^{::clerk/visibility {:result :hide}}
(def coupling-deps
  (coupling-factors->deps coupling-factors))

^{::clerk/visibility {:result :hide}}
(def coupling-tree
  (-> coupling-factors
      (get file-path)
      keys
      ->coupling-tree))

(clerk/table
 (clerk/use-headers
  (concat [["from" "to" "factor"]]
          (->> coupling-deps
               (take 20)
               (map (fn [{:keys [source target value]}]
                      [source target value]))
               (sort-by (juxt #(- (nth % 2)) first second))))))

(tree-deps-plot
 {:data {:tree coupling-tree
         :deps coupling-deps}
  :id :path
  :label :path
  :labels? :leaves})
