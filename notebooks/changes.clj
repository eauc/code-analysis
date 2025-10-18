(ns changes
  {:nextjournal.clerk/visibility {:code :hide :result :show}}
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string]
            [clj-async-profiler.core :as prof]
            [files.deltas :refer [deltas-join-commits filter-since]]
            [files.modules :refer [->modules file-nodes-with-module]]
            [files.tree :refer [files->nodes filter-max-depth]]
            [graphs.trees :refer [tree-plot]]
            [metrics.changes :refer [file-nodes-with-changes]]
            [metrics.core :refer [metric->color top-files-list metric->str]]
            [metrics.complexity :refer [file-nodes-with-complexity filter-min-complexity complexity->tree-plot-value]]
            [nextjournal.clerk :as clerk]
            [tick.core :as t]))

; # Changes

(def example
  "tree-sitter")

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
       (map #(vector (:hash %) %))
       (into {})))

^{::clerk/visibility {:result :hide}}
(def file-deltas
  (prof/profile
    (-> (:file-deltas log)
        (select-keys (keys file-stats))
        (update-vals #(->> %
                           (deltas-join-commits [:date] commits)
                           (filter-since (config :since)))))))

^{::clerk/visibility {:result :hide}}
(def metrics
  [["Relative Σchurn" #(-> % :changes :churn double (/ (max 1 (-> % :complexity :lines))))]])

^{::clerk/visibility {:result :hide}}
(def base-nodes
  (->> (keys file-stats)
       (files->nodes example)
       (file-nodes-with-module (config :modules))
       (filter-max-depth (config :max-depth))
       (file-nodes-with-complexity file-stats)
       (filter-min-complexity :lines (config :min-complexity))
       (file-nodes-with-changes file-deltas)))

(->> metrics
      (mapcat
       (fn [[title metric]]
         (let [nodes (mapv #(assoc % :metric (metric %)) base-nodes)
               file-nodes (filterv #(= (:type %) :file) nodes)]
           [(clerk/html
             [:div
              [:h3 title]
              [:p "File with max changes:"]
              (top-files-list :metric file-nodes)])
            (tree-plot
             {:nodes nodes
              :id :path
              :label (fn [{:keys [depth metric path]}]
                       (str depth " - " path "<br />" (metric->str metric)))
              :color (metric->color :metric file-nodes)
              :value (complexity->tree-plot-value nodes)
              :max-depth -1})]))))
