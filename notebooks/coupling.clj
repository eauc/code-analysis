(ns coupling
  {:nextjournal.clerk/visibility {:code :hide :result :show}}
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string]
            [clj-async-profiler.core :as prof]
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
                               [:crates {:match #"^crates/[^/]+/?" :max-depth 2}]
                               [:lib {:match #"^lib/[^/]+/?" :max-depth 3}]
                               [:test {:match #"^test/[^/]+(?:/[^/]+/?)"}]])}
     "metabase" {:max-depth 1
                 :min-complexity 10000
                 :modules (->modules
                           [[:frontend {:match #"^frontend/[^/]+/" :max-depth 4}]
                            [:src {:match #"^src/metabase/" :max-depth 2}]
                            [:entreprise {:match #"^enterprise/[^/]+/" :max-depth 2}]
                            [:resources {:match #"^resources/[^/]+/" :max-depth 2}]
                            [:docs {:match #"^docs/" :max-depth 2}]
                            [:e2e {:match #"^e2e/[^/]+/" :max-depth 2}]
                            [:test {:match #"^test/[^/]+/?" :max-depth 3}]
                            [:dev {:match #"^dev/"}]
                            [:modules {:match #"^modules/"}]
                            [:test-modules {:match #"^test_modules/"}]
                            [:patches {:match #"^patches/"}]
                            [:release {:match #"^release/"}]
                            [:util {:match #"^util/"}]
                            [:misc {:match #"^[^./][^/]*/"}]])}
     "nvim" {:max-depth 1
             :min-complexity 2000
             :modules (->modules
                       [[:src-nvim {:match #"^src/nvim/" :max-depth 3}]
                        [:src {:match #"^src/[^/]+/"}]
                        [:runtime-doc {:match #"^runtime/doc/" :max-depth 4}]
                        [:runtime-lua {:match #"^runtime/lua/" :max-depth 4}]
                        [:runtime {:match #"^runtime/[^/]+/" :max-depth 2}]
                        [:test {:match #"^test/[^/]+/" :max-depth 3}]
                        [:misc {:match #"^[^/]+/"}]])}
     "zig" {:min-complexity 10000
            :max-depth 1
            :modules (->modules
                      [[:libc-include {:match #"^lib/libc/include/" :max-depth 4}]
                       [:lib-include {:match #"^lib/include/" :max-depth 3}]
                       [:lib-std {:match #"^lib/std/" :max-depth 3}]
                       [:lib {:match #"^lib/" :max-depth 2}]
                       [:src-codegen {:match #"^src/codegen/" :max-depth 3}]
                       [:src {:match #"^src/" :max-depth 2}]
                       [:test {:match #"^test/[^/]+/" :max-depth 2}]
                       [:doc {:match #"^doc/"}]
                       [:misc {:match #"^[^./][^/]*/?"}]])}}
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
       (filter #(t/< (config :since) (:date %)))))

^{::clerk/visibility {:result :hide}}
(def file-deltas
  (-> (:file-deltas log)
      (select-keys (keys file-stats))))

^{::clerk/visibility {:result :hide}}
(def files
  (keys file-stats))

^{::clerk/visibility {:result :hide}}
(def module-paths
  (files->module-paths (config :modules) files))

^{::clerk/visibility {:result :hide}}
(def coupling-factors
  (prof/profile
   (->coupling-factors file-deltas module-paths)))

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
       (files->nodes example)
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
