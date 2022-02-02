(ns girouette.garden.util
  (:require [clojure.spec.alpha :as s]
            [clojure.walk :as walk]
            #?(:clj [garden.types]
               :cljs [garden.types :refer [CSSAtRule]])
            [girouette.util :as util])
  #?(:clj (:import (garden.types CSSAtRule))))

(declare merge-rules)

(defn- merge-similar-rules [[rule-type x y] values]
  (case rule-type
    :simple       [x (apply merge values)]
    :pseudo-class [x [y (apply merge values)]]
    :media        (assoc-in x [:value :rules] (merge-rules (apply concat values)))
    :unknown      values))

(defn- rule-info [rule]
  (condp s/valid? rule
    (s/tuple string? map?)
    {:ident [:simple (first rule)]
     :value (second rule)}

    (s/tuple string? (s/tuple keyword? map?))
    {:ident [:pseudo-class (first rule) (first (second rule))]
     :value (second (second rule))}

    (s/and (s/keys :req-un [::identifier ::value])
           #(= :media (:identifier %)))
    {:ident [:media (update rule :value dissoc :rules)]
     :value (get-in rule [:value :rules])}

    {:ident [:unknown rule]
     :value rule}))

(defn merge-rules
  "Combine garden rules that have the same selectors."
  [rules]
  (->> rules
       (map rule-info)
       (util/group-by :ident :value)
       (map (fn [[ident values]]
              (merge-similar-rules ident values)))))

(defn apply-class-rules
  "Returns a collection of garden rules defining `target-class-name` as an aggregation of `gi-garden-rules`.
   `target-class-name` is the dotted CSS class name which we want to define.
   `gi-garden-rules` is an ordered collection of garden rules generated by Girouette.
   `gi-class-names` is an ordered collection of the CSS dotted class-names defined in `gi-garden-rules`."
  [target-class-name gi-garden-rules gi-class-names]
  (->> (map (fn [rule rule-class-name]
              (walk/postwalk (fn [x]
                               (if (= rule-class-name x)
                                 target-class-name
                                 x))
                             rule))
            gi-garden-rules
            gi-class-names)
       merge-rules))

(defn rule-comparator
  "Compares the Garden rules provided by Girouette,
   so they can be ordered correctly in a style file."
  [rule1 rule2]
  (let [is-media-query1 (and (instance? CSSAtRule rule1)
                             (= (:identifier rule1) :media))
        is-media-query2 (and (instance? CSSAtRule rule2)
                             (= (:identifier rule2) :media))]
    (compare [is-media-query1 (-> rule1 meta :girouette/component :ordering-level)]
             [is-media-query2 (-> rule2 meta :girouette/component :ordering-level)])))

