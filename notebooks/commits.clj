(ns commits
  {:nextjournal.clerk/visibility {:code :hide :result :show}}
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.pprint :as pprint]
            [clojure.string]
            [files.modules :refer [->modules file-nodes-with-module]]
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

(def example
  "zig")

(def config
  (merge
   {:max-depth 1000
    :min-complexity 1
    :since (-> (t/zoned-date-time) (t/<< (t/of-years 10)) t/date)}
   ({"tree-sitter" {:max-depth 1
                    :min-complexity 200
                    :modules (->modules
                              [[:crates-generate {:match #"^crates/generate/" :max-depth 4}]
                               [:crates-cli {:match #"^crates/cli/" :max-depth 4}]
                               [:crates {:match #"^crates/" :max-depth 2}]
                               [:lib {:match #"^lib/" :max-depth 3}]])}
     "metabase" {:max-depth 1
                 :min-complexity 10000
                 :modules (->modules
                           [[:frontend {:match #"^frontend/" :max-depth 4}]
                            [:src {:match #"^src/" :max-depth 2}]
                            [:entreprise {:match #"^enterprise/" :max-depth 2}]
                            [:resources {:match #"^resources/" :max-depth 2}]
                            [:docs {:match #"^docs/" :max-depth 2}]
                            [:e2e {:match #"^e2e/" :max-depth 2}]
                            [:test {:match #"^test/" :max-depth 3}]])}
     "nvim" {:min-complexity 2000
             :modules (->modules
                       [[:src-nvim {:match #"^src/nvim/" :max-depth 3}]
                        [:runtime-doc {:match #"^runtime/doc/" :max-depth 4}]
                        [:runtime-lua {:match #"^runtime/lua/" :max-depth 4}]
                        [:runtime {:match #"^runtime/" :max-depth 2}]
                        [:test {:match #"^test/" :max-depth 3}]])}
     "zig" {:min-complexity 10000
            :max-depth 1
            :modules (->modules
                      [[:libc-include {:match #"^lib/libc/include/" :max-depth 4}]
                       [:lib-include {:match #"^lib/include/" :max-depth 3}]
                       [:lib-std {:match #"^lib/std/" :max-depth 3}]
                       [:lib {:match #"^lib/" :max-depth 2}]
                       [:src-codegen {:match #"^src/codegen/" :max-depth 3}]
                       [:src {:match #"^src/" :max-depth 2}]
                       [:test {:match #"^test/" :max-depth 2}]])}}
    example)))

; ### Commits log data
(def log
  (edn/read (java.io.PushbackReader. (io/reader (str "/home/manu/code/perso/code_analysis/code_analysis/examples/" example "/log.edn")))))

; ### Files stats data
(def file-stats
  (edn/read (java.io.PushbackReader. (io/reader (str "/home/manu/code/perso/code_analysis/code_analysis/examples/" example "/file_stats.edn")))))

^{::clerk/visibility {:result :hide}}
(def commits
  (->> (:commits log)
       (mapv #(update % :date (comp t/date t/instant)))
       (filter #(t/< (config :since) (:date %)))
       commits-with-type))

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
      :series (mapv #(vector % % (commit-type->color %)) (remove #{:unknown} ordered-commit-types))
      :title (str name " / type over time")
      :stacked? true})]))

; ## Fixes hotspots

^{::clerk/visibility {:result :hide}}
(def files
  (keys file-stats))

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
       (files->nodes example)
       (file-nodes-with-module (config :modules))
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
