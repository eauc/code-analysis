(ns age
  {:nextjournal.clerk/visibility {:code :hide :result :show}}
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.math :refer [sqrt]]
            [clojure.string]
            [files.modules :refer [->modules file-nodes-with-module]]
            [files.tree :refer [files->nodes filter-max-depth]]
            [graphs.bars :refer [v-bars]]
            [graphs.trees :refer [tree-plot]]
            [metrics.age :refer [dates->age-stats file-nodes-with-age-stats]]
            [metrics.core :refer [->metric blue->red red->green metric->color top-files-list]]
            [metrics.complexity :refer [file-nodes-with-complexity filter-min-complexity complexity->tree-plot-value]]
            [nextjournal.clerk :as clerk]
            [tick.core :as t]))

; # Age

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

; ### Files stats data
(def file-stats
  (edn/read (java.io.PushbackReader. (io/reader (str "/home/manu/code/perso/code_analysis/code_analysis/examples/" example "/file_stats.edn")))))

^{::clerk/visibility {:result :hide}}
(def now
  (t/date))

; ## Age distribution

^{::clerk/visibility {:result :hide}}
(def line-ages
  (->> file-stats
       vals
       (mapv :dates)
       (apply merge-with +)))

(v-bars
 {:data (->> line-ages
             (group-by (fn [[date _]] (t/between (t/date date) now :months)))
             (mapv (fn [[months date-ns]] [months (->metric second date-ns)]))
             (into {}))
  :title "Lines age"})

^{::clerk/visibility {:result :hide}}
(def file-age-stats
  (update-vals
   file-stats
   (comp dates->age-stats :dates)))

^{::clerk/visibility {:result :hide}}
(def metrics
  [["last modification" #(t/between (:newest %) now :months)]
   ["lines age p90" #(t/between (:p90 %) now :months)]
   ["modification range" #(t/between (:oldest %) (:newest %) :months)]])

(->> metrics
     (map
      (fn [[title metric]]
        (v-bars
         {:data (->> file-age-stats
                     vals
                     (remove nil?)
                     (mapv metric)
                     (frequencies))
          :title (str "Files / " title)}))))

; ## Age maps

^{::clerk/visibility {:result :hide}}
(def base-nodes
  (->> (keys file-stats)
       (files->nodes example)
       (file-nodes-with-module (config :modules))
       (filter-max-depth (config :max-depth))
       (file-nodes-with-complexity file-stats)
       (filter-min-complexity :lines (config :min-complexity))
       (file-nodes-with-age-stats file-stats)))

^{::clerk/visibility {:result :hide}}
(def metrics
  [["Last modification"
    #(t/between (-> % :age :newest) now :days)
    #(str (t/<< now (t/of-days %)) " (" % " days)")
    blue->red]
   ["p90 age"
    #(t/between (-> % :age :p90) now :days)
    #(str (t/<< now (t/of-days %)) " (" % " days)")
    blue->red]
   ; ["Median age"
   ;  #(t/between (-> % :age :median) now :days)
   ;  #(str (t/<< now (t/of-days %)) " (" % " days)")
   ;  blue->red]
   ["Creation date"
    #(t/between (-> % :age :oldest) now :days)
    #(str (t/<< now (t/of-days %)) " (" % " days)")
    blue->red]
   ["Modification range"
    #(t/between (-> % :age :oldest) (-> % :age :newest) :days)
    #(str % " days")
    red->green]])

(->> metrics
     (mapcat
      (fn [[title metric metric->str color-scale]]
        (let [nodes (mapv #(assoc % :metric (metric %)) base-nodes)]
          [(clerk/html
            [:div
             [:h3 title]
             [:p "Top 10 files:"]
             [:ul
              (top-files-list :metric nodes)]])
           (tree-plot
            {:nodes nodes
             :id :path
             :label (fn [{:keys [depth path metric]}]
                      (str depth " - " path "<br /> " (metric->str metric)))
             :color (metric->color (comp sqrt :metric) nodes color-scale)
             :value (complexity->tree-plot-value nodes)
             :max-depth -1})]))))
