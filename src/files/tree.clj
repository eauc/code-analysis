(ns files.tree
  {:nextjournal.clerk/visibility {:code :hide :result :show}}
  (:require [clojure.string]))

(defn files->tree
  [files]
  (->> files
       (reduce
        (fn [tree path]
          (let [frags (clojure.string/split path #"/")
                sub-paths (reductions #(str %1 "/" %2) frags)]
            (assoc-in tree sub-paths path)))
        {})))

(defn tree->nodes
  ([parent frag child]
   (if (string? child)
     [{:parent parent :path frag :type :file}]
     (concat [{:parent parent :path frag :type :directory}]
             (mapcat
              (fn [[k v]]
                (tree->nodes frag k v))
              child))))
  ([root-name tree]
   (mapcat
    (fn [[k v]] (tree->nodes root-name k v))
    tree)))

(defn files->nodes
  [root-name files]
  (->> files
       files->tree
       (tree->nodes root-name)))
