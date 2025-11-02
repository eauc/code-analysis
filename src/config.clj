(ns config
  (:require [clojure.edn :as edn]
            [files.modules :refer [->modules]]
            [tick.core :as t]))

(defn ->config
  [raw project-name]
  (let [c (merge
           {:log-path (str "examples/" project-name "/log.edn")
            :file-stats-path (str "examples/" project-name "/file_stats.edn")
            :words-path (str "examples/" project-name "/words.edn")
            :max-depth 1000
            :min-complexity 1
            :root #"^"
            :exclude-identifiers #{}
            :time-start-years 30
            :time-stop-months 0}
           (raw project-name))
        start-time (-> (t/zoned-date-time) (t/<< (t/of-years (c :time-start-years))) t/date)
        stop-time (-> (t/date) (t/<< (t/of-months (c :time-stop-months))))]
    (assoc c
           :start-time (c :start-time start-time)
           :stop-time (c :stop-time stop-time))))

(defn read!
  [project-name]
  (let [raw (->> (slurp "examples/config.edn")
                 (edn/read-string {:readers {'pattern re-pattern
                                             'modules ->modules
                                             'inst/date t/date}}))]
    (->config raw project-name)))

(defn filter-files-map
  [{:keys [root filter-paths exclude-paths] :as _config} files-map]
  (cond->> files-map
    :always
    (filterv (fn [[path _]]
               (re-find root path)))

    :always
    (map (fn [[path v]]
           (let [root-prefix (re-find root path)]
             [(subs path (count root-prefix)) v])))

    (seq filter-paths)
    (filter (fn [[path _]]
              (some #(re-find % path) filter-paths)))

    (seq exclude-paths)
    (remove (fn [[path _]]
              (some #(re-find % path) exclude-paths)))

    :always
    (into {})))
