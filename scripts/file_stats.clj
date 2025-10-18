#!/usr/bin/env bb

; git-config core.quotePath = false
; git ls-tree --full-tree --name-only -r HEAD > files.txt
; ./scripts/file_stats.clj ~/code/xplo/tree-sitter < files.txt > file_stats.edn

(ns scripts.file-stats
  (:require [clojure.java.io :as io]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [babashka.fs :as fs]
            [babashka.tasks :refer [shell]]
            [tick.core :as t]
            [utils.core :refer [sum-by count-by]]))

(defn- debug
  [& args]
  (binding [*out* *err*]
    (apply println args)))

(defn count-indents
  [l]
  (let [[_ indent] (re-matches #"^(\s*).*$" l)]
    (count indent)))

(defn count-parens
  [l]
  (count (filterv #{\( \{ \[} l)))

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
        [_ email date] (re-matches #"^[^(]*\(<(?<email>[^>]*)>\s+(?<date>[^\s]+)\s+\d+$" blame)]
    ; (prn email date content)
    {:email email
     :date date
     :content (or content "")
     :line line}))

(defn parse-date
  [line]
  (let [{date-str :date} line]
    (try
      (t/instant date-str)
      (catch Exception err
        (throw (ex-info "Invalid date format." {:date-str date-str :line line} err))))))

(let [[root-path] *command-line-args*
      file-names (line-seq (io/reader *in*))
      total-count (count file-names)
      cache-path (fs/path root-path ".file_stats_cache.edn")
      data (try
             (-> (fs/file cache-path)
                 slurp
                 edn/read-string)
             (catch Exception _ {}))]
  (prn
   (loop [[f & rest] file-names
          i 1
          result data]
     (if-not f
       result
       (if (get result f)
         (do
           (debug (str i "/" total-count) f "-- cached")
           (recur rest (inc i) result))
         (do
           (debug (str i "/" total-count) f)
           (let [lines (->> (blame-file root-path f)
                            :out str/split-lines
                            (remove empty?)
                            (mapv parse-blame-line))
                 indents (sum-by (comp count-indents :content) lines)
                 parens (sum-by (comp count-parens :content) lines)
                 indent-parens (sum-by (comp #(* (count-indents %) (count-parens %)) :content) lines)
                 authors (count-by :email lines)
                 dates (count-by (comp str t/date parse-date) lines)
                 file-stats {:complexity {:lines (-> lines count)
                                          :indents indents
                                          :parens parens
                                          :indent-parens indent-parens}
                             :authors authors
                             :dates dates}
                 result (assoc result f file-stats)]
             (fs/write-lines cache-path [(pr-str result)])
             (recur rest (inc i) result))))))))
