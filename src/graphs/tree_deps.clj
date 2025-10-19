(ns graphs.tree-deps
  (:require [nextjournal.clerk :as clerk]))

(defn datum-id
  [{id-k :id
    :or {id-k :id}}]
  (str "datum." (name id-k)))

(defn layout
  [{:keys [width height radius]
    :or {width 1000
         height 800
         radius 200}
    :as opts}]
  {:width width
   :height height
   :autosize :none
   :signals [{:name :layout :value :cluster}
             {:name :radius :value radius}
             {:name :originX :update "width / 2"}
             {:name :originY :update "height / 2"}
             {:name :tension :value 0.85}
             {:name :active
              :value nil
              :on [{:events "text:pointerover" :update (datum-id opts)}
                   {:events "pointerover[!event.item]" :update "null"}]}]})

(defn tree-data
  [{id-k :id
    :or {id-k :id}}
   tree]
  [{:name :tree
    :values tree
    :transform [{:type :stratify :key id-k :parentKey :parent}
                {:type :tree 
                 :method {:signal :layout} 
                 :size [1 1] 
                 :as [:alpha :beta :depth :children]}
                {:type :formula :expr "(360 * datum.alpha + 270) % 360" :as :angle}
                {:type :formula :expr "inrange(datum.angle, [90, 270])" :as :leftside}
                {:type :formula :expr "originX + radius * datum.beta * cos(PI * datum.angle / 180)" :as :x}
                {:type :formula :expr "originY + radius * datum.beta * sin(PI * datum.angle / 180)" :as :y}]}
   {:name :leaves
    :source :tree
    :transform [{:type :filter :expr "!datum.children"}]}])

(defn tree-marks
  [{label-k :label
    :or {label-k :label}
    :as opts}
   _]
  [{:type :text
    :from {:data :tree}
    :encode {:enter {:text {:field label-k}
                     :baseline {:value :middle}}
             :update {:x {:field :x}
                      :y {:field :y}
                      :dx {:signal "2 * (datum.leftside ? -1 : 1)"}
                      :angle {:signal "datum.leftside ? datum.angle - 180 : datum.angle"}
                      :align {:signal "datum.leftside ? 'right' : 'left'"}
                      :fontSize {:value 12}
                      :fontWeight [{:test (str "indata('selected', 'source', " (datum-id opts) ")") :value :bold}
                                   {:test (str "indata('selected', 'target', " (datum-id opts) ")") :value :bold}
                                   {:value nil}]
                      :fill {:value :black}}}}])

(defn deps-data
  [_ deps]
  [{:name :dependencies
    :values deps
    :transform [{:type :formula :expr "treePath('tree', datum.source, datum.target)" :as :treepath :initonly true}]}
   {:name :selected
    :source :dependencies
    :transform [{:type :filter :expr "datum.source === active || datum.target === active"}]}])

(defn deps-marks
  [_ _]
  [{:type :group
    :from {:facet {:name :path
                   :data :dependencies
                   :field :treepath}}
    :marks [{:type :line
             :interactive false
             :from {:data :path}
             :encode {:enter {:interpolate {:value :bundle}}
                      :update {:x {:field :x}
                               :y {:field :y}
                               :stroke {:scale :color :field {:parent :value}}
                               :strokeWidth [{:test "parent.target === active" :value 4}
                                             {:test "parent.source === active" :value 4}
                                             {:value 1}]
                               :strokeOpacity [{:test "parent.source === active" :value 1}
                                               {:test "parent.target === active" :value 1}
                                               {:value 0.3}]
                               :tension {:signal :tension}}}}]}])

(defn deps-color-scales
  [_ _]
  [{:name "color"
    :type "ordinal"
    :range {:scheme "redyellowgreen"}
    :domain {:data :dependencies :field :value :sort true}
    :reverse true}])

(defn tree-deps-plot
  [{:keys [data]
    :as opts}]
  (let [{:keys [tree deps]} data]
    (clerk/vl
     (merge
      (layout opts)
      {:data (concat
              (tree-data opts tree)
              (deps-data opts deps))
       :scales (deps-color-scales opts deps)
       :marks (concat
               (tree-marks opts tree)
               (deps-marks opts deps))}))))
