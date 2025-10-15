#!/usr/bin/env bb

; git ls-tree --full-tree --name-only -r HEAD > files.txt
; ./scripts/file_stats.clj ~/code/xplo/tree-sitter < files.txt > file_stats.edn

(ns scripts.file-stats
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [babashka.fs :as fs]
            [babashka.tasks :refer [shell]]
            [tick.core :as t]
            [utils.core :refer [sum-by count-by]]))

(defn count-indents
  [l]
  (let [[_ indent] (re-matches #"^(\s*).*$" l)]
    (count indent)))

(defn count-parens
  [l]
  (count (filter #{\( \{ \[} l)))

(defn blame-file
  [root-path file-path]
  (let [full-path (fs/path root-path file-path)
        options (->> ["-M" ; track moves
                      "-e" ; authors as email
                      "-w" ; ignore spaces
                      "--date=iso-strict"]
                     (str/join " "))
        cmd (str "git -C " root-path " blame " options " \"" full-path "\"")]
    (try
      (shell cmd {:out :string})
      (catch Exception _ []))))

(defn parse-blame-line
  [line]
  ; (prn line)
  (let [[blame content] (str/split line #"\)\s")
        [_ email date] (re-matches #"^[^\s]+\s+(?:[^\s]+\s+)?\(<(?<email>[^>]+)>\s+(?<date>[^\s]+)\s+\d+$" blame)]
    ; (prn email date content)
    {:email email
     :date date
     :line (or content "")}))

(let [[root-path] *command-line-args*
      file-names (line-seq (io/reader *in*))]
  (->> file-names
       (mapv
        (fn [f]
          (let [lines (->> (blame-file root-path f)
                           :out str/split-lines
                           (remove empty?)
                           (map parse-blame-line))
                indents (sum-by (comp count-indents :line) lines)
                parens (sum-by (comp count-parens :line) lines)
                indent-parens (sum-by (comp #(* (count-indents %) (count-parens %)) :line) lines)
                authors (count-by :email lines)
                dates (count-by (comp str t/date t/instant :date) lines)]
            [f {:complexity {:lines (-> lines count)
                             :indents indents
                             :parens parens
                             :indent-parens indent-parens}
                :authors authors
                :dates dates}])))
       (into {})
       prn))
