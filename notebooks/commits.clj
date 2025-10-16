(ns commits
  {:nextjournal.clerk/visibility {:code :hide :result :show}}
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string]
            [graphs.pies :refer [pie]]
            [graphs.plots :refer [plots]]
            [graphs.trees :refer [tree-plot]]
            [metrics.commits.types :refer [commit-type->color commits-with-type ordered-commit-types]]
            [metrics.commits.words :refer [commit-words commit-func-words]]
            [metrics.core :refer [->metric-by ->time-serie cumulative-sum]]
            [nextjournal.clerk :as clerk]
            [tick.core :as t]))

(def example
  "tree-sitter")

; ## Commits log data
^{::clerk/no-cache true}
(def log
  (edn/read (java.io.PushbackReader. (io/reader (str "/home/manu/code/perso/code_analysis/code_analysis/examples/" example "/log.edn")))))

; # Commits

^{::clerk/visibility {:result :hide}}
(def authors
  (:authors log))

^{::clerk/visibility {:result :hide}}
(def commits
  (->> (:commits log)
       (map #(update % :date (comp t/date t/instant)))
       commits-with-type))

^{::clerk/visibility {:result :hide}}
(->> commits
     (filter #(= :unknown (:type %)))
     (remove #(re-find #"patch" (:description %)))
     (map :description))

^{::clerk/visibility {:result :hide}}
(def file-deltas
  (:file-deltas log))

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
      :series (map #(vector % % (commit-type->color %)) (remove #{:unknown} ordered-commit-types))
      :title (str name " / type over time")
      :stacked? true})]))

; ## Descriptions

; All words from commit descriptions

(tree-plot
 {:nodes (->> commits
              commit-words
              (->metric-by (constantly 1) clojure.string/lower-case)
              (sort-by second)
              reverse
              (take 30)
              (map (fn [[word count]] {:id word :parent "word-map" :value count})))})

; Functional words from commit descriptions

(tree-plot
 {:nodes (->> commits
              commit-func-words
              (->metric-by (constantly 1) clojure.string/lower-case)
              (sort-by second)
              reverse
              (take 30)
              (map (fn [[word count]] {:id word :parent "word-map" :value count})))})
