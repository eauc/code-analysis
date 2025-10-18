(ns files.modules
  {:nextjournal.clerk/visibility {:code :hide :result :show}}
  (:require [clojure.string]))

(defn ->modules
  [lst]
  (map
   (fn [[name opts]]
     [name (assoc opts :name name)])
   lst))

(defn node->module
  [modules {:keys [path]}]
  (->> modules
       (filterv (fn [[_ {:keys [match]}]] (re-find match path)))
       first
       second))

(defn file-nodes-with-module
  [modules file-nodes]
  (->> file-nodes
       (mapv #(assoc % :module (node->module modules %)))))
