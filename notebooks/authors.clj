(ns authors
  {:nextjournal.clerk/visibility {:code :hide :result :show}}
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string]
            [files.tree :refer [files->nodes]]
            [graphs.bars :refer [h-bars]]
            [graphs.colors :refer [colors-for]]
            [graphs.plots :refer [plots]]
            [graphs.trees :refer [tree-plot]]
            [metrics.core :refer [->metric-by ->time-serie cumulative-sum]]
            [metrics.authors :refer [->authors-stats file-nodes-with-author]]
            [metrics.complexity :refer [file-nodes-with-complexity]]
            [tick.core :as t]
            [nextjournal.clerk :as clerk]))

(def example
  "tree-sitter")

; ## Commits log data
^{::clerk/no-cache true}
(def log
  (edn/read (java.io.PushbackReader. (io/reader (str "/home/manu/code/perso/code_analysis/code_analysis/examples/" example "/log.edn")))))

; ## Files complexity data
^{::clerk/no-cache true}
(def file-stats
  (edn/read (java.io.PushbackReader. (io/reader (str "/home/manu/code/perso/code_analysis/code_analysis/examples/" example "/file_stats.edn")))))

; # Authors

^{::clerk/visibility {:result :hide}}
(def authors
  (:authors log))

^{::clerk/visibility {:result :hide}}
(def commits
  (->> (:commits log)
       (map #(update % :date (comp t/date t/instant)))))

^{::clerk/visibility {:result :hide}}
(def file-deltas
  (:file-deltas log))

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

^{::clerk/visibility {:result :hide}}
(def top-author-emails
  (->> top-authors (map :email) set))

^{::clerk/visibility {:result :hide}}
(def commits-for-top-authors
  (filter (comp top-author-emails :author) commits))

(doall
 (for [[metric name] [[(constantly 1) "#commits"]
                      [:diff "diff"]]]
   (let [metrics (->> commits
                      (->time-serie #(->metric-by metric :author %))
                      (cumulative-sum))]
     (plots
      {:data metrics
       :series (map #(vector (:email %) (:author %)) top-authors)
       :title (str "Cumulative " name " over time")
       :stacked? true}))))

^{::clerk/visibility {:result :hide}}
(def author->color
  (colors-for (map :author top-authors)))

; ## File authors

(let [files (keys file-stats)
      nodes (->> files
                 (files->nodes example)
                 (file-nodes-with-complexity file-stats :lines)
                 (file-nodes-with-author authors file-stats))]
  (tree-plot
   {:type :treemap
    :nodes nodes
    :id :path
    :value :complexity
    :color (comp author->color :author)
    :label (fn [{:keys [type path author]}]
             (let [basename (-> path (clojure.string/split #"/") last)]
               (str basename (when (= type :directory) "/") "<br />" author)))
    :max-depth 3}))
