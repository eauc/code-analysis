(ns data.file-stats
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [config :refer [filter-files-map]]))

(defn project-file-stats
  [config file-stats]
  (filter-files-map config file-stats))

(defn read!
  [config]
  (let [file-stats (-> (config :file-stats-path)
                       io/reader
                       java.io.PushbackReader.
                       edn/read)]
    (project-file-stats config file-stats)))
