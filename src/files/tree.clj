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

(defn basename
  [path]
  (-> path (clojure.string/split #"/") last))

(defn tree->nodes
  ([depth parent frag children]
   (if (string? children)
     [{:path frag
       :type :file
       :basename (basename frag)
       :parent parent
       :depth depth
       :leaves [frag]}]
     (let [path (str frag "/")
           child-nodes (mapcat #(apply tree->nodes (inc depth) path %) children)
           leaves (->> child-nodes
                       (filterv #(= path (:parent %)))
                       (mapcat :leaves))]
       (concat [{:path path
                 :type :directory
                 :basename (-> path basename (str "/"))
                 :parent parent
                 :depth depth
                 :leaves leaves}]
               child-nodes))))

  ([root-name tree]
   (mapcat
    (fn [[k v]] (tree->nodes 1 root-name k v))
    tree)))

(defn files->nodes
  [root-name files]
  (->> files
       files->tree
       (tree->nodes root-name)))

(defn child-nodes
  [path file-nodes]
  (filterv #(= (:parent %) path) file-nodes))

(defn filter-paths
  [paths file-nodes]
  (->> file-nodes
       (filter (fn [{:keys [path]}]
                 (some
                  #(clojure.string/starts-with? % path)
                  paths)))))

(defn filter-max-depth
  [max-depth file-nodes]
  (->> file-nodes
       (filterv #(>= (-> % :module :max-depth (or max-depth))
                     (:depth %)))))
