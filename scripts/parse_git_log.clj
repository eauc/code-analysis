#!/usr/bin/env bb

; git log
; --format="format:===%nhash: %H%nauthor: %aN%nauthor-email: %aE%ndate: %aI%nparent: %P%ndescription: %s%n---"
; --no-merges --numstat -M25% --ignore-blank-lines --ignore-all-space
; > log.txt
; ./scripts/parse_git_log.clj < log.txt > log.edn

(ns parse-git-log
  (:require [clojure.java.io :as io]
            [clojure.set]
            [clojure.string]
            [tick.core :as t]
            [utils.core :refer [sum-by]]))

(defn debug
  [& args]
  (binding [*out* *err*]
    (apply println args)))

(defmulti fmt-prop first)

(defmethod fmt-prop
  :default
  [[_ v]]
  v)

(defmethod fmt-prop
  :author-date
  [[_ v]]
  (t/instant v))

(def prop-pattern
  #"^([^:]+): (.+)$")

(defn- parse-prop
  [line]
  (let [[_ prop-name prop-value] (re-matches prop-pattern line)]
    [(keyword prop-name) prop-value]))

(defn- parse-props
  [lines]
  (->> lines
       (mapv parse-prop)
       (mapv (fn [[k v]] [k (fmt-prop [k v])]))
       (into {})))

(def file-deltas-pattern
  #"^(\d+|-)\t(\d+|-)\t(.*)$")

(def file-move-subpath-pattern
  #"^([^{]*)\{(.*) => (.*)\}(.*)$")

(def file-move-fullpath-pattern
  #"^(.+) => (.+)$")

(defn- parse-file-name
  [raw-str]
  (if-let [[_ prefix sub-from sub-to suffix] (re-matches file-move-subpath-pattern raw-str)]
    (let [from (-> (str prefix sub-from suffix) (clojure.string/replace "//" "/"))
          to (-> (str prefix sub-to suffix) (clojure.string/replace "//" "/"))]
      [to [from to]])
    (if-let [[_ from to] (re-matches file-move-fullpath-pattern raw-str)]
      [to [from to]]
      [raw-str])))

(defn- parse-file-deltas
  [line]
  (let [[_ added deleted file-name] (re-matches file-deltas-pattern line)
        binary? (= "-" added)
        [file-name moves] (parse-file-name file-name)
        added (if binary? 0 (Integer/parseInt added))
        deleted (if binary? 0 (Integer/parseInt deleted))]
    [file-name
     (merge {:added added
             :deleted deleted
             :diff (- added deleted)
             :edits (+ added deleted)
             :churn (min added deleted)}
            (when binary? {:binary? binary?}))
     moves]))

(defn- parse-deltas
  [lines]
  (let [delta-tuples (mapv parse-file-deltas lines)
        deltas (->> delta-tuples
                    (mapv (fn [[file-name deltas]] [file-name deltas]))
                    (into {}))
        moves (->> delta-tuples
                   (mapv #(nth % 2))
                   (into {}))]
    (merge
     {:deltas deltas}
     (when (seq moves)
       {:moves moves}))))

(defn- split-separator
  [sep lines]
  (->> lines
       (partition-by #{sep})
       (remove #{[sep]})))

(defn- parse-log
  [lines]
  (let [[prop-lines delta-lines] (->> lines (remove #{""}) (split-separator "---"))]
    (merge
     (parse-props prop-lines)
     (parse-deltas delta-lines))))

(def logs
  (let [lines (line-seq (io/reader *in*))]
    (->> lines
         (split-separator "===")
         (mapv parse-log))))

(def authors
  (let [authors (->> logs
                     (mapv (juxt :author-email :author))
                     (distinct)
                     (group-by first))]
    (update-vals authors #(mapv second %))))

(def moves
  (->> logs
       (mapv :moves)
       (filterv identity)
       reverse))

(defn- -final-name
  [moves file-name]
  (let [[move & rest] (drop-while #(not (get % file-name)) moves)
        to (get move file-name)]
    (if-not (seq to)
      file-name
      (-final-name rest to))))

(def final-name
  (memoize (partial -final-name moves)))

(def file-aliases
  (let [file-aliases (->> logs
                          (mapcat (comp keys :deltas))
                          distinct
                          (mapv #(vector % (final-name %)))
                          (remove (fn [[k v]] (= k v)))
                          (group-by second))]
    (update-vals file-aliases #(->> % (mapv first) set))))

(defn- log->commit
  [log]
  (let [{:keys [deltas]} log
        deltas (mapv second deltas)]
    (-> log
        (select-keys [:hash :author-email :date :description])
        (clojure.set/rename-keys {:author-email :author})
        (assoc :added (sum-by :added deltas)
               :deleted (sum-by :deleted deltas)
               :churn (sum-by :churn deltas)
               :diff (sum-by :diff deltas)
               :edits (sum-by :edits deltas)))))

(def commits
  (->> logs
       (mapv log->commit)))

(def file-deltas
  (let [file-deltas (->> logs
                         (mapcat (fn [{:keys [deltas hash author date]}] (update-vals deltas #(assoc % :hash hash))))
                         (mapv (fn [[commit-name commit-deltas]]
                                (let [f-name (final-name commit-name)]
                                  [f-name (cond-> commit-deltas (not= f-name commit-name) (assoc :as commit-name))])))
                         (group-by first))]
    (update-vals file-deltas #(mapv second %))))

(prn
 {:commits commits
  :file-aliases file-aliases
  :file-deltas file-deltas
  :authors authors})
