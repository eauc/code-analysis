(ns data.file-stats
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]))

(defn project-file-stats
  [{:keys [root filter-paths exclude-paths] :as _config} file-stats]
  (cond->> file-stats
    :always
    (filterv (fn [[path _]]
               (re-find root path)))

    :always
    (map (fn [[path v]]
           (let [root-prefix (re-find root path)]
             [(subs path (count root-prefix)) v])))

    (seq filter-paths)
    (filter (fn [[path _]]
              (some #(re-find % path) filter-paths)))

    (seq exclude-paths)
    (remove (fn [[path _]]
              (some #(re-find % path) exclude-paths)))

    :always
    (into {})))

(defn read!
  [config]
  (let [file-stats (-> (config :file-stats-path)
                       io/reader
                       java.io.PushbackReader.
                       edn/read)]
    (project-file-stats config file-stats)))
