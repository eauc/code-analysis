(ns metrics.commits.types
  {:nextjournal.clerk/visibility {:code :hide :result :show}}
  (:require [clojure.string]
            [files.deltas :refer [deltas-join-commits]]
            [metrics.core :refer [->metric-by]]))

(def ordered-commit-types
  [:feat
   :weak-feat
   :refacto
   :weak-refacto
   :weak-fix
   :fix
   :test
   :perfs
   :doc
   :types
   :tools
   :ci
   :version
   :unknown])

(defn commit-type->color
  [type]
  (get {:feat "#006f3c"
        :weak-feat "#27b376"
        :fix "#ff270e"
        :weak-fix "#ff7f0e"
        :refacto "#9467bd"
        :weak-refacto "#ef9fff"
        :test "#176796"
        :doc "#67beff"
        :types "#97beff"
        :perfs "#17becf"
        :tools "#FFFF00"
        :ci "#CCCC00"
        :version "#999900"}
       type "grey"))

(defn commit-type
  [{:keys [description]}]
  (let [description (clojure.string/lower-case description)]
    (cond
      ; keywords
      (re-find #"(?:^|\W)(bug|fix|revert|typo|error)" description) :fix
      (re-find #"(?:^|\W)(refacto|review|tech|requested change|recycle)" description) :refacto
      (re-find #"(?:^|\W)(feat)" description) :feat
      (re-find #"(?:^|\W)test" description) :test
      (re-find #"(?:^|\W)perf" description) :perfs
      (re-find #"(?:^|\W)ci" description) :ci
      (re-find #"(?:^|\W)docs?" description) :doc
      (re-find #"(?:^|\W)types?" description) :types
      (re-find #"(?:^|\W)(chore|build|lint)" description) :tools
      (re-find #"(?:^|\W)version" description) :version
      ; words combinations
      (and (re-find #"(?:^|\W)add" description)
           (re-find #"(?:^|\W)remove" description)) :refacto
      ; unique words - weak
      (re-find #"(?:^|\W)(avoid|correc|guard|workaround|check|🐛)" description) :weak-fix
      (re-find #"(?:^|\W)(add|enhance|implement)" description) :feat
      (re-find #"(?:^|\W)(cli|implem|make|new|parse|sparkles|✨|style|use|wip)" description) :weak-feat
      (re-find #"(?:^|\W)(allow|clean|delete|drop|improve|move|remove|rename?|restruct|rework|simplify|swap|update)" description) :weak-refacto
      (re-find #"(?:^|\W)(backport|bump|upgrade|lock|dependenc|merge)" description) :weak-tools
      ; pattern
      (re-find #"(?:^|\W)(?:v|ver|version)?\d+\.\d+(?:\.\d+)?(?:\W|$)" description) :version
      :else :unknown)))

(defn commits-with-type
  [commits]
  (mapv #(assoc % :type (commit-type %)) commits))

(defn file-nodes-with-commit-type-edits
  [commits file-deltas file-nodes]
  (->> file-nodes
       (mapv (fn [{:keys [leaves] :as node}]
              (let [fixes (->> leaves
                               (mapcat file-deltas)
                               (deltas-join-commits [:type] commits)
                               (->metric-by :edits :type))]
                (assoc node :type->edits fixes))))))
