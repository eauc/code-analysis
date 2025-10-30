(ns config
  (:require [clojure.edn :as edn]
            [files.modules :refer [->modules]]
            [tick.core :as t]))

(defn ->config
  [raw project-name]
  (let [c (merge
           {:log-path (str "examples/" project-name "/log.edn")
            :file-stats-path (str "examples/" project-name "/file_stats.edn")
            :max-depth 1000
            :min-complexity 1
            :root #"^"
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
