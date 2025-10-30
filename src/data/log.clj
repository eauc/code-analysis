(ns data.log
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [tick.core :as t]))

(defn- project-file-deltas
  [{:keys [filter-paths exclude-paths] :as _config} file-deltas]
  (->> file-deltas
       (filter (fn [[path _]]
                 (some #(re-find % path) filter-paths)))
       (remove (fn [[path _]]
                 (some #(re-find % path) exclude-paths)))
       (into {})))

(defn- hash->change-stats
  [file-deltas]
  (let [groups (->> file-deltas
                    (mapcat second)
                    (group-by :hash))]
    (update-vals
     groups
     (fn [deltas]
       (->> deltas
            (map #(select-keys % [:added :churn :deleted :diff :edits]))
            (apply merge-with +))))))

(defn- project-commits
  [file-deltas commits]
  (let [hash->deltas (hash->change-stats file-deltas)
        hashes (-> hash->deltas keys set)]
    (->> commits
         (filter (comp hashes :hash))
         (mapv #(merge % (-> % :hash hash->deltas))))))

(defn read!
  [config]
  (let [{:keys [authors commits file-deltas]}
        (-> (config :log-path)
            io/reader
            java.io.PushbackReader.
            edn/read)
        file-deltas (project-file-deltas config file-deltas)
        commits (->> (project-commits file-deltas commits)
                     (mapv #(update % :date (comp t/date t/instant))))]
    {:authors authors
     :commits commits
     :file-deltas file-deltas}))
