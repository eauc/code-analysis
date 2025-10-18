(ns metrics.commits.words
  {:nextjournal.clerk/visibility {:code :hide :result :show}}
  (:require [clojure.string]))

(defn commit-words
  [commits]
  (->> commits
       (mapv :description)
       (mapcat #(clojure.string/split % #"[^\w]+"))
       (remove #(re-find #"(?i)^(|.|an|as|to|by|in|it|if|is|be|on|the|no|do|don|all|from|for|are|into|this|that|there|these|and|not|of|get|use|using|less|some|more|we|when|with|\d+)$" %))))

(defn commit-func-words
  [commits]
  (->> commits
       commit-words
       (remove #(re-find #"(?i)^(implement|refacto|cleanup|fix|ci|new|error|update|check|make|build|remove|debug|patch|function|deps|tests|e2e|bump|add|refactor|docs|feat|doc|test)$" %))))
