(ns metrics.commits.words
  {:nextjournal.clerk/visibility {:code :hide :result :show}}
  (:require
   [clojure.string :as str]
   [metrics.core :refer [->metric-by]]))

(defn commit-words
  [commits]
  (->> commits
       (mapv :description)
       (mapcat #(str/split % #"[^\w]+"))
       (remove #(re-find #"(?i)^(|.|an|as|to|by|in|it|if|is|be|on|one|the|no|et|en|do|du|de|des|un|une|tu|le|la|les|qui|sans|avec|pas|pour|sur|dans|don|all|from|for|are|into|this|that|there|these|and|not|of|get|use|using|less|some|more|we|when|with|\d+)$" %))))

(defn ->word-frequencies
  [commits]
  (->> commits
       commit-words
       (->metric-by (constantly 1) str/lower-case)
       (sort-by second)
       reverse
       (take 30)
       (mapv (fn [[word count]]
               {:id word :parent "word-map" :value count}))))

(defn commit-func-words
  [commits]
  (->> commits
       commit-words
       (remove #(re-find #"(?i)^(pr|ok|ko|implement|refacto|clean|cleanup|chore|tech|code|rename|move|create|log|review|change|skip|try|fix|ci|new|error|update|check|make|build|remove|debug|patch|function|deps|tests|e2e|bump|add|refactor|docs|feat|doc|test|dependen)$" %))))

(defn ->func-word-frequencies
  [commits]
  (->> commits
       commit-func-words
       (->metric-by (constantly 1) str/lower-case)
       (sort-by second)
       reverse
       (take 30)
       (mapv (fn [[word count]]
               {:id word :parent "word-map" :value count}))))
