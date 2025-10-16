(ns metrics.commits.types
  {:nextjournal.clerk/visibility {:code :hide :result :show}}
  (:require [clojure.string]
            [metrics.core :refer [->metric]]))

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
      (re-find #"(?:^|[^\w])(fix|revert|typo|error)" description) :fix
      (re-find #"(?:^|[^\w])refacto" description) :refacto
      (re-find #"(?:^|[^\w])(feat)" description) :feat
      (re-find #"(?:^|[^\w])test" description) :test
      (re-find #"(?:^|[^\w])perf" description) :perfs
      (re-find #"(?:^|[^\w])ci" description) :ci
      (re-find #"(?:^|[^\w])docs?" description) :doc
      (re-find #"(?:^|[^\w])(chore|build|lint)" description) :tools
      (re-find #"(?:^|[^\w])version" description) :version
      ; words combinations
      (and (re-find #"(?:^|[^\w])add" description)
           (re-find #"(?:^|[^\w])remove" description)) :refacto
      ; unique words - weak
      (re-find #"(?:^|[^\w])(avoid|correc|guard|workaround)" description) :weak-fix
      (re-find #"(?:^|[^\w])(add|enhance|implement)" description) :feat
      (re-find #"(?:^|[^\w])(cli|make|parse|style|use)" description) :weak-feat
      (re-find #"(?:^|[^\w])(allow|clean|delete|drop|improve|move|remove|rename|restruct|rework|simplify|swap|update)" description) :weak-refacto
      (re-find #"(?:^|[^\w])(backport|bump|upgrade|lock|dependenc|merge)" description) :weak-tools
      ; pattern
      (re-find #"(?:^|[^\w])(?:v|ver|version)?\d+\.\d+(?:\.\d+)?(?:[^\w]|$)" description) :version
      :else :unknown)))

(defn commits-with-type
  [commits]
  (map #(assoc % :type (commit-type %)) commits))

