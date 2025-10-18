(ns authors
  {:nextjournal.clerk/visibility {:code :hide :result :show}}
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string]
            [files.modules :refer [->modules file-nodes-with-module]]
            [files.tree :refer [files->nodes filter-max-depth]]
            [graphs.bars :refer [h-bars]]
            [graphs.colors :refer [colors-for]]
            [graphs.pies :refer [pie]]
            [graphs.plots :refer [plots]]
            [graphs.trees :refer [tree-plot]]
            [metrics.core :refer [->metric-by ->time-serie cumulative-sum]]
            [metrics.authors :refer [email->author ->authors-stats file-nodes-with-author]]
            [metrics.complexity :refer [file-nodes-with-complexity filter-min-complexity complexity->tree-plot-value]]
            [tick.core :as t]
            [nextjournal.clerk :as clerk]))

; # Authors

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
(def authors
  (:authors log))

^{::clerk/visibility {:result :hide}}
(def commits
  (->> (:commits log)
       (mapv #(update % :date (comp t/date t/instant)))))

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
(def files
  (keys file-stats))

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

; ^{::clerk/visibility {:result :hide}}
(def nodes
  (->> files
       (files->nodes example)
       (file-nodes-with-module (config :modules))
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
