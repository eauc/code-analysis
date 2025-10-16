(ns metrics.commits.words
  {:nextjournal.clerk/visibility {:code :hide :result :show}}
  (:require [clojure.string]))

(defn commit-words
  [commits]
  (->> commits
       (map :description)
       (mapcat #(clojure.string/split % #"[^\w]+"))
       (remove #(re-find #"^(|.|an|to|by|in|into|on|the|from|no|do|don|for|it|if|is|be|are|as|this|that|there|these|and|not|of|get|use|using|less|some|more|when|with|\d+)$" %))))

(def not-func-words
  #{"add"
    "build"
    "bump"
    "check"
    "ci"
    "debug"
    "deps"
    "doc"
    "docs"
    "feat"
    "fix"
    "function"
    "implement"
    "make"
    "new"
    "patch"
    "refacto"
    "refactor"
    "remove"
    "update"
    "test"
    "tests"
    "error"})

(defn commit-func-words
  [commits]
  (->> commits
       commit-words
       (remove (comp not-func-words clojure.string/lower-case))))
