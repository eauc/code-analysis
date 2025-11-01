(ns author-xrays
  {:nextjournal.clerk/visibility {:code :hide :result :show}}
  (:require
   [config :as cfg]
   [data.file-stats]
   [data.log]
   [files.modules :refer [file-nodes-with-module-config]]
   [files.tree :refer [files->nodes filter-max-depth]]
   [graphs.pies :refer [pie]]
   [graphs.plots :refer [plots]]
   [graphs.trees :refer [tree-plot]]
   [graphs.tree-deps :refer [tree-deps-plot]]
   [metrics.authors :refer [email->author file-nodes-with-author-ratio]]
   [metrics.commits.types :refer [commits-with-type commit-type->color ordered-commit-types]]
   [metrics.commits.words :refer [->func-word-frequencies]]
   [metrics.complexity :refer [file-nodes-with-complexity filter-min-complexity complexity->tree-plot-value]]
   [metrics.core :refer [->metric-by ->time-serie cumulative-sum metric->str metric->color white->blue]]
   [metrics.coupling :refer [coupling-factors->deps ->coupling-tree]]
   [nextjournal.clerk :as clerk]))

; # Author X-Rays

; Project

(def project-name
  "tree-sitter")

; Author

(def author
  "Max Brunsfeld")

; Config

^::clerk/no-cache
(def config
  (cfg/read! project-name))

; ### Commits log data

(def log
  (data.log/read! config))

; ### File stats data

(def file-stats
  (data.file-stats/read! config))

^{::clerk/visibility {:result :hide}}
(def files
  (keys file-stats))

^{::clerk/visibility {:result :hide}}
(def authors
  (:authors log))

^{::clerk/visibility {:result :hide}}
(def author-email
  (->> authors
       (filter (fn [[_ [name]]]
                 (= name author)))
       first
       first))

^{::clerk/visibility {:result :hide}}
(def commits
  (->> (:commits log)
       (filterv (fn [{:keys [author]}]
                  (= author author-email)))
       commits-with-type))

; ## Commit types

^{::clerk/visibility {:result :hide}}
(def metrics
  [[(constantly 1) "#commits"]
   [:diff "Diff"]
   [:edits "Edits"]
   [:churn "Churn"]])

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

; ## Files ownership

^{::clerk/visibility {:result :hide}}
(def nodes
  (->> files
       (files->nodes project-name)
       (file-nodes-with-module-config (config :modules))
       (filter-max-depth (config :max-depth))
       (file-nodes-with-complexity file-stats)
       (filter-min-complexity :lines (config :min-complexity))
       (file-nodes-with-author-ratio author-email file-stats)
       (mapv (fn [{:keys [author-ratio] :as node}]
               (assoc node :metric author-ratio)))))

(tree-plot
 {:type :treemap
  :nodes nodes
  :id :path
  :color (metric->color :metric nodes white->blue)
  :label (fn [{:keys [depth path metric]}]
           (str depth " - " path "<br />" (metric->str metric)))
  :value (complexity->tree-plot-value nodes)
  :max-depth -1})

; ## Authors coupling

^{::clerk/visibility {:result :hide}}
(def coupling-counts
  (->> file-stats
       (filterv (fn [[_ {:keys [authors]}]]
                  (< 0 (get authors author-email 0))))
       (mapv (fn [[_ {:keys [authors]}]]
               (let [author-lines (get authors author-email)]
                 (update-vals authors #(min % author-lines)))))
       (apply merge-with +)))

^{::clerk/visibility {:result :hide}}
(def coupling-factors
  (let [author-count (get coupling-counts author-email)]
    {author (-> coupling-counts 
                (update-keys #(email->author % authors))
                (update-vals #(double (/ % author-count))))}))

^{::clerk/visibility {:result :hide}}
(def coupling-tree
  (->> (get coupling-factors author)
       keys
       ->coupling-tree))

^{::clerk/visibility {:result :hide}}
(def coupling-deps
  (coupling-factors->deps coupling-factors))

(clerk/table
 (clerk/use-headers
  (concat [["from" "to" "factor"]]
          (->> coupling-deps
               (take 20)
               (map (fn [{:keys [source target value]}]
                      [author (if (= target author) source target) value]))
               (sort-by (juxt #(- (nth % 2)) first second))))))

(tree-deps-plot
 {:data {:tree coupling-tree
         :deps coupling-deps}
  :id :path
  :label :path})
