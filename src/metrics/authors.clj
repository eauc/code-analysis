(ns metrics.authors
  {:nextjournal.clerk/visibility {:code :hide :result :show}}
  (:require [clojure.string]
            [utils.core :refer [sum-by]]
            [utils.date :refer [min-date max-date]]))

(defn email->author
  [email authors]
  (-> email authors first))

(defn ->authors-stats
  [authors commits]
  (->> authors
       (map (fn [[email _]]
              (let [commits (->> commits (filter #(= email (:author %))))]
                {:author (email->author email authors)
                 :email email
                 :added (sum-by :added commits)
                 :deleted (sum-by :deleted commits)
                 :edits (sum-by :edits commits)
                 :diff (sum-by :diff commits)
                 :churn (sum-by :churn commits)
                 :first-contrib (->> commits (map :date) (reduce min-date) str)
                 :last-contrib (->> commits (map :date) (reduce max-date) str)})))
       (sort-by :edits)
       reverse))

(defn ->file-authors
  [files file-stats]
  (->> files
       (map (fn [path] [path (get-in file-stats [path :authors])]))
       (into {})))

(defn path-author
  [path file-authors]
  (->> file-authors
       (filter #(clojure.string/starts-with? (first %) path))
       (map second)
       (apply merge-with +)
       (sort-by second)
       last
       first))

(defn file-nodes-with-author
  [authors file-stats file-nodes]
  (let [files (->> file-nodes (filter #(= (:type %) :file)) (map :path))
        file-authors (->file-authors files file-stats)]
    (map
     (fn [{:keys [path] :as node}]
       (let [author (-> path (path-author file-authors) (email->author authors))]
         (assoc node :author author)))
     file-nodes)))
