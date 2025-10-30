(ns data.file-stats
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]))

(defn project-file-stats
  [{:keys [filter-paths exclude-paths] :as _config} file-stats]
  (->> file-stats
       (filter (fn [[path _]]
                 (some #(re-find % path) filter-paths)))
       (remove (fn [[path _]]
                 (some #(re-find % path) exclude-paths)))
       (into {})))

(defn read!
  [config]
  (let [file-stats (-> (config :file-stats-path)
                       io/reader
                       java.io.PushbackReader.
                       edn/read)]
    (project-file-stats config file-stats)))
