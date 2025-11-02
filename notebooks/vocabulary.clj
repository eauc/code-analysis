(ns vocabulary
  {:nextjournal.clerk/visibility {:code :hide :result :show}}
  (:require
   [clojure.string]
   [config :as cfg]
   [data.file-stats]
   [data.log]
   [data.words]
   [files.modules :refer [file-nodes-with-module-config]]
   [files.tree :refer [files->nodes filter-max-depth]]
   [graphs.bars :refer [v-bars]]
   [graphs.colors :refer [colors-for]]
   [graphs.pies :refer [pie]]
   [graphs.trees :refer [tree-plot]]
   [metrics.complexity :refer [file-nodes-with-complexity filter-min-complexity complexity->tree-plot-value]]
   [metrics.words :refer [->word-ratios ->identifier-stats ->value-stats file-nodes-with-main-identifier]]
   [nextjournal.clerk :as clerk]))

; # Vocabulary

; Project

(def project-name
  "tree-sitter")

; Config

^::clerk/no-cache
(def config
  (cfg/read! project-name))

; ### Files stats data

(def file-stats
  (data.file-stats/read! config))

^{::clerk/visibility {:result :hide}}
(def files
  (keys file-stats))

; ### Nodes data

(def words
  (data.words/read! config))

^{::clerk/visibility {:result :hide}}
(def identifiers
  (:identifiers words))

^{::clerk/visibility {:result :hide}}
(def values
  (:values words))

; ## Words ratios

(pie
 {:data (->word-ratios file-stats {:identifiers identifiers :values values})})

; ## Frequencies distribution

^{::clerk/visibility {:result :hide}}
(def identifier-stats
  (->> identifiers
       (remove (fn [[path _]]
                 (some #(re-find % path) (config :test-paths))))
       (->identifier-stats config)))

(v-bars
 {:data (->> identifier-stats (map second) frequencies)
  :title "Identifiers frequencies"})

^{::clerk/visibility {:result :hide}}
(def value-stats
  (->> values
       (remove (fn [[path _]]
                 (some #(re-find % path) (config :test-paths))))
       ->value-stats))

(v-bars
 {:data (->> value-stats (map second) frequencies)
  :title "Values frequencies"})

; ## Identifiers

^{::clerk/visibility {:result :hide}}
(def top-identifiers
  (->> identifier-stats
       (take 100)))

(tree-plot
 {:nodes (->> top-identifiers
              (mapv (fn [[name count]]
                      {:id name :parent "identifiers-map" :value count})))})

; ## Values

^{::clerk/visibility {:result :hide}}
(def top-values
  (->> value-stats
       (take 100)))

(tree-plot
 {:nodes (->> top-values
              (mapv (fn [[name count]]
                      {:id name :parent "values-map" :value count})))})

; ## Main identifiers file map

^{::clerk/visibility {:result :hide}}
(def nodes
  (->> files
       (files->nodes project-name)
       (file-nodes-with-module-config (config :modules))
       (filter-max-depth (config :max-depth))
       (file-nodes-with-complexity file-stats)
       (filter-min-complexity :lines (config :min-complexity))
       (file-nodes-with-main-identifier config identifiers)
       (mapv (fn [{:keys [main-identifier] :as node}]
               (assoc node :metric main-identifier)))))

^{::clerk/visibility {:result :hide}}
(def word-colors
  (colors-for (->> nodes
                   (map :metric)
                   distinct)))

(tree-plot
 {:nodes nodes
  :id :path
  :label (fn [{:keys [depth metric path]}]
           (str depth " - " path "<br />" metric))
  :color (comp word-colors :metric)
  :value (complexity->tree-plot-value nodes)})
