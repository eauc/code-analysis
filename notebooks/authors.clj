(ns authors
  {:nextjournal.clerk/visibility {:code :hide :result :show}}
  (:require
   [clojure.string]
   [config :as cfg]
   [data.file-stats]
   [data.log] 
   [files.modules :refer [file-nodes-with-module-config]]
   [files.tree :refer [files->nodes filter-max-depth]]
   [graphs.bars :refer [h-bars]]
   [graphs.colors :refer [colors-for]]
   [graphs.pies :refer [pie]]
   [graphs.plots :refer [plots]]
   [graphs.trees :refer [tree-plot]]
   [metrics.core :refer [->metric-by ->time-serie cumulative-sum]]
   [metrics.authors :refer [email->author ->authors-stats file-nodes-with-author]]
   [metrics.complexity :refer [file-nodes-with-complexity filter-min-complexity complexity->tree-plot-value]]
   [nextjournal.clerk :as clerk]))

; # Authors

; Project

(def example
  "tree-sitter")

; Config

^::clerk/no-cache
(def config
  (cfg/read! example))

; ### Commits log data

(def log
  (data.log/read! config))

; ### Files stats data

(def file-stats
  (data.file-stats/read! config))

^{::clerk/visibility {:result :hide}}
(def authors
  (:authors log))

; ### Files

^{::clerk/visibility {:result :hide}}
(def files
  (keys file-stats))

^{::clerk/visibility {:result :hide}}
(def commits
  (:commits log))

; ## Commits authors

^{::clerk/visibility {:result :hide}}
(def authors-stats
  (->authors-stats authors commits))

^{::clerk/visibility {:result :hide}}
(def top-authors
  (take 10 authors-stats))

(clerk/table
 (clerk/use-headers
  (concat [["author" "email" "first contrib" "last contrib" "total edits" "added" "deleted" "diff" "churn"]]
          (map
           (fn [{:keys [author email first-contrib last-contrib edits added deleted diff churn]}]
             [author email first-contrib last-contrib edits added deleted diff churn])
           top-authors))))

(h-bars
 {:title "Main Authors"
  :data (reverse top-authors)
  :names :author
  :series [[:edits "Edits"]
           [:diff "Diffs"]
           [:churn "Churn"]]})

(doall
 (for [[metric name] [[(constantly 1) "#commits"]
                      [:diff "diff"]
                      [:churn "churn"]]]
   (let [metrics (->> commits
                      (->time-serie #(->metric-by metric :author %))
                      (cumulative-sum))]
     (plots
      {:data metrics
       :series (mapv #(vector (:email %) (:author %)) top-authors)
       :title (str "Cumulative " name " over time")
       :stacked? true}))))

; ## File authors

^{::clerk/visibility {:result :hide}}
(def top-authors
  (->> (select-keys file-stats files)
       (mapv (comp :authors second))
       (apply merge-with +)
       (sort-by #(- (second %)))
       (take 10)
       (mapv (fn [[email n-lines]]
               [(email->author email authors) n-lines]))))

^{::clerk/visibility {:result :hide}}
(def author->color
  (colors-for (mapv first top-authors)))

(pie
 {:data (into {} top-authors)
  :colors author->color
  :order (mapv first top-authors)})

^{::clerk/visibility {:result :hide}}
(def nodes
  (->> files
       (files->nodes example)
       (file-nodes-with-module-config (config :modules))
       (filter-max-depth (config :max-depth))
       (file-nodes-with-complexity file-stats)
       (filter-min-complexity :lines (config :min-complexity))
       (file-nodes-with-author authors file-stats)))

(tree-plot
 {:type :treemap
  :nodes nodes
  :id :path
  :color (comp author->color :author)
  :label (fn [{:keys [depth path author]}]
           (str depth " - " path "<br />" author))
  :value (complexity->tree-plot-value nodes)
  :max-depth -1})

; TODO bus factor treemap
