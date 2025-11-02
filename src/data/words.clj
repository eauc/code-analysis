(ns data.words
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [config :refer [filter-files-map]]))

(defn project-words
  [config words]
  (let [identifiers (->> (:identifiers words)
                         (filter-files-map config))
        values (->> (:values words)
                    (filter-files-map config))]
    {:identifiers identifiers
     :values values}))

(defn read!
  [config]
  (let [words (-> (config :words-path)
                  io/reader
                  java.io.PushbackReader.
                  edn/read)]
    (project-words config words)))
