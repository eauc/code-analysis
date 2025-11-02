(ns metrics.words)

(defn ->word-ratios
  [file-stats {:keys [identifiers values] :as _file-word-stats}]
  (let [file-paths
        (-> (concat (keys identifiers)
                    (keys values))
            set)
        total-file-chars
        (->> (select-keys file-stats file-paths)
             vals
             (map #(get-in % [:complexity :chars]))
             (reduce +))
        identifier-chars
        (->> (vals identifiers)
             (apply merge-with +)
             (map (fn [[name c]] (* (count name) c)))
             (reduce +))
        value-chars
        (->> (vals values)
             (apply merge-with +)
             (map (fn [[name c]] (* (count name) c)))
             (reduce +))]
    {:identifiers identifier-chars
     :values value-chars
     :language (- total-file-chars identifier-chars value-chars)}))

(defn ->identifier-stats
  [{:keys [exclude-identifiers] :as _config} file-indentifiers-stats]
  (->> (vals file-indentifiers-stats)
       (apply merge-with +)
       (remove (fn [[name _]] (exclude-identifiers name)))
       (sort-by #(- (second %)))))

(defn ->value-stats
  [file-value-stats]
  (->> (vals file-value-stats)
       (apply merge-with +)
       (sort-by #(- (second %)))))

(defn file-nodes-with-main-identifier
  [config file-identifier-stats nodes]
  (->> nodes
       (mapv (fn [{:keys [leaves] :as node}]
               (let [[identifier]
                     (->> (select-keys file-identifier-stats leaves)
                          (->identifier-stats config)
                          first)]
                 (assoc node :main-identifier identifier))))))
