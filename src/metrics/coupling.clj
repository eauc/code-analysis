(ns metrics.coupling
  (:require [clojure.string]
            [files.tree :refer [files->nodes filter-paths]]))

(defn hash->files
  [file-deltas]
  (->> file-deltas
       (mapcat (fn [[file deltas]] (map (fn [{:keys [hash]}] [hash file]) deltas)))
       (group-by first)
       (map (fn [[hash pairs]]
              [hash (->> pairs (map second) distinct)]))
       (into {})))

(defn file-match-module?
  [file-path module-path]
  (clojure.string/starts-with? file-path module-path))

(defn file->module-path
  [files module-paths]
  (->> files
       (mapv (fn [file-path]
               [file-path
                (->> module-paths
                     (filterv (partial file-match-module? file-path))
                     (sort-by count)
                     last)]))
       (remove (comp nil? second))
       (into {})))

(defn module-deltas
  [module-path file-deltas]
  (->> file-deltas
       (filterv #(-> % first (file-match-module? module-path)))
       (mapcat second)))

(defn coupled-modules
  [hash->files file->module-path {:keys [hash]}]
  (->> (hash->files hash)
       (mapv file->module-path)
       (remove nil?)
       distinct))

(defn ->coupling-counts
  [file-deltas module-paths]
  (let [files (keys file-deltas)
        hash->files (hash->files file-deltas)
        _ (prn hash->files)
        file->module-path (file->module-path files module-paths)]
    (->> module-paths
         (mapv (fn [module-path]
                 [module-path
                  (->> file-deltas
                       (module-deltas module-path)
                       (mapcat (partial coupled-modules hash->files file->module-path))
                       frequencies)]))
         (into {}))))

(defn coupling-count->factor
  [file-modification-count coupling-count]
  (/ (double coupling-count)
     file-modification-count))

(defn ->coupling-factors
  [file-deltas module-paths]
  (let [coupling-counts (->coupling-counts file-deltas module-paths)]
    (->> coupling-counts
         (mapv (fn [[file file-coupling-counts]]
                 (let [file-modification-count (get file-coupling-counts file)]
                   [file (update-vals file-coupling-counts (partial coupling-count->factor file-modification-count))])))
         (mapv (fn [[path file-couplings]]
                 [path (dissoc file-couplings path)]))
         (into {}))))

(defn coupling-factors->deps
  [coupling-factors]
  (->> coupling-factors
       (mapcat (fn [[file file-couplings]]
                 (->> file-couplings
                      (mapv (fn [[other-file factor]]
                              [(sort [file other-file]) factor])))))
       (remove (fn [[[source target] _]] (= source target)))
       (group-by first)
       (mapv (fn [[[source target] fs]]
               {:source source
                :target target
                :value (->> fs (mapv second) (apply max))}))
       (sort-by #(- (:value %)))))
       ; (filterv (fn [{:keys [value]}] (< 0.5 value)))))

(defn ->coupling-tree
  [files module-paths]
  (->> files
       (files->nodes ".")
       (filter-paths module-paths)
       (concat [{:path "."}])))

(defn ->coupling-scores
  [hash->files file-deltas]
  (->> file-deltas
       (map (fn [[file-path deltas]]
              (let [coupling-counts (->> deltas
                                         (mapcat (comp hash->files :hash))
                                         frequencies)
                    file-count (get coupling-counts file-path)
                    coupling-counts (dissoc coupling-counts file-path)]
                [file-path
                 (/ (->> coupling-counts vals (reduce +) double)
                    file-count)])))
       (into {})))

(defn file-nodes-with-coupling-scores
  [file-deltas file-nodes]
  (let [hash->files (hash->files file-deltas)
        coupling-scores (->coupling-scores hash->files file-deltas)]
    (->> file-nodes
         (map (fn [{:keys [leaves] :as node}]
                (assoc node :couplings (->> leaves
                                            (map coupling-scores)
                                            (remove nil?)
                                            (reduce +))))))))
