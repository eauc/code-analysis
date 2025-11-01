(ns metrics.authors
  {:nextjournal.clerk/visibility {:code :hide :result :show}}
  (:require [clojure.string]
            [utils.core :refer [sum-by]]
            [utils.date :refer [min-date max-date]]))

(defn email->author
  [email authors]
  (-> email authors (or [email]) first))

(defn ->authors-stats
  [authors commits]
  (->> authors
       (mapv (fn [[email _]]
               (let [commits (->> commits (filterv #(= email (:author %))))]
                 {:author (email->author email authors)
                  :email email
                  :added (sum-by :added commits)
                  :deleted (sum-by :deleted commits)
                  :edits (sum-by :edits commits)
                  :diff (sum-by :diff commits)
                  :churn (sum-by :churn commits)
                  :first-contrib (->> commits (mapv :date) (reduce min-date) str)
                  :last-contrib (->> commits (mapv :date) (reduce max-date) str)})))
       (sort-by :edits)
       reverse))

(defn ->file-authors
  [authors file-stats]
  (->> file-stats
       (mapv (comp :authors second))
       (apply merge-with +)
       (sort-by #(- (second %)))
       (mapv (fn [[email n-lines]]
               [(email->author email authors) n-lines]))))

(defn path-author
  [path file-authors]
  (->> file-authors
       (filterv #(clojure.string/starts-with? (first %) path))
       (mapv second)
       (apply merge-with +)
       (sort-by second)
       last
       first))

(defn file-nodes-with-author
  [authors file-stats file-nodes]
  (->> file-nodes
       (mapv (fn [{:keys [leaves] :as node}]
               (let [email (->> leaves
                                (mapv (comp :authors file-stats))
                                (apply merge-with +)
                                (sort-by #(- (second %)))
                                first
                                first)]
                 (assoc node :author (email->author email authors)))))))

(defn file-nodes-with-author-ratio
  [author-email file-stats file-nodes]
  (->> file-nodes
       (mapv (fn [{:keys [leaves] :as node}]
               (let [total-lines (->> leaves
                                      (map (comp :lines :complexity file-stats))
                                      (reduce +))
                     author-lines (->> leaves
                                       (map #(get-in file-stats [% :authors author-email] 0))
                                       (reduce +))]
                 (assoc node :author-ratio (double (/ author-lines total-lines))))))))
