(ns metrics.complexity
  {:nextjournal.clerk/visibility {:code :hide :result :show}}
  (:require [clojure.string]))

(defn file-complexity
  [files-complexity path type]
  (get-in files-complexity [path :complexity type]))

(defn file-nodes-with-complexity
  [files-complexity complexity-type file-nodes]
  (map
   (fn  [{:keys [path type] :as node}]
     (let [complexity (if (= type :file) 
                        (file-complexity files-complexity path complexity-type)
                        0)] 
       (assoc node :complexity complexity)))
   file-nodes))
