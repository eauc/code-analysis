; {:nextjournal.clerk/visibility {:code :hide :result :hide}}
; (set! *warn-on-reflection* true)
; (set! *unchecked-math* :warn-on-boxed)
(ns user
  (:require [clojure.string]
            [clj-async-profiler.core :as prof]
            [nextjournal.clerk :as clerk]
            [nrepl.server :as nrepl]))

(defn serve!
  [& _]
  (nrepl/start-server :bind "127.0.0.1" :port 12345)
  (prof/serve-ui 8080)
  (clerk/serve!
   {:browse? true
    ; :show-filter-fn #(clojure.string/starts-with? % "notebooks")
    :watch-paths ["notebooks" "src"]}))
