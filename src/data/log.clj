(ns data.log
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [tick.core :as t]))

(defn- project-file-deltas
  [{:keys [filter-paths exclude-paths] :as _config} commits file-deltas]
  (let [hashes (->> commits (map :hash) set)]
    (->> file-deltas
         (filter (fn [[path _]]
                   (some #(re-find % path) filter-paths)))
         (remove (fn [[path _]]
                   (some #(re-find % path) exclude-paths)))
         (map (fn [[path deltas]]
                [path (filterv (comp hashes :hash) deltas)]))
         (into {}))))

(defn- deltas->change-stats
  [deltas]
  (->> deltas
       (map #(select-keys % [:added :churn :deleted :diff :edits]))
       (apply merge-with +)))

(defn- hash->change-stats
  [file-deltas]
  (let [groups (->> file-deltas
                    (mapcat second)
                    (group-by :hash))]
    (update-vals groups deltas->change-stats)))

(defn- project-commits
  [file-deltas commits]
  (let [hash->change-stats (hash->change-stats file-deltas)
        hashes (-> hash->change-stats keys set)]
    (->> commits
         (filterv (comp hashes :hash))
         (mapv #(merge % (-> % :hash hash->change-stats))))))

(defn read!
  [{:keys [start-time stop-time] :as config}]
  (let [{:keys [authors commits file-deltas]}
        (-> (config :log-path)
            io/reader
            java.io.PushbackReader.
            edn/read)
        commits (->> commits
                     (mapv #(update % :date (comp t/date t/instant)))
                     (filterv #(t/<= start-time (:date %) stop-time)))
        file-deltas (project-file-deltas config commits file-deltas)
        commits (project-commits file-deltas commits)]
    {:authors authors
     :commits commits
     :file-deltas file-deltas}))
