#!/usr/bin/env bb

; sg scan --json=stream | jq -c '{text,file,ruleId}' > words.txt
; ./scripts/parse_words.clj < words.json > words.edn

(ns scripts.parse-words
  (:require
   [cheshire.core :as json]
   [clojure.java.io :as io]))

(defn- debug
  [& args]
  (binding [*out* *err*]))

(def words
  (let [lines (->> (io/reader *in*)
                   (line-seq)
                   (map #(json/parse-string % true)))

        identifiers-by-files
        (->> lines
             (filter #(re-find #"in?dentifier" (:ruleId %)))
             (group-by :file))
        identifier-stats
        (update-vals
         identifiers-by-files
         (fn [words]
           (->> words
                (map :text)
                (frequencies))))

        values-by-files
        (->> lines
             (filter #(re-find #"value" (:ruleId %)))
             (group-by :file))
        value-stats
        (update-vals
         values-by-files
         (fn [words]
           (->> words
                (map :text)
                (frequencies))))]

    {:identifiers identifier-stats
     :values value-stats}))

(prn words)
