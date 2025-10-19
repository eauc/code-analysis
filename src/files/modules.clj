(ns files.modules
  {:nextjournal.clerk/visibility {:code :hide :result :show}}
  (:require [clojure.string]))

(defn ->modules
  [lst]
  (map
   (fn [[name opts]]
     [name (assoc opts :name name)])
   lst))

(defn match-module?
  [path [_ {:keys [match]}]]
  (re-find match path))

(defn node->module-config
  [modules {:keys [path]}]
  (->> modules
       (filterv (partial match-module? path))
       first
       second))

(defn file-nodes-with-module-config
  [modules file-nodes]
  (->> file-nodes
       (mapv #(assoc % :module (node->module-config modules %)))))

(defn files->module-paths
  [modules files]
  (->> files
       (map (fn [path]
              (let [res (some (partial match-module? path) modules)]
                (if (vector? res) (second res) res))))
       (remove nil?)
       distinct))
