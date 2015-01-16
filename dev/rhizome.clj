(ns rhizome
  (:require
   [clojure.pprint :refer (pprint)]
   [rhizome.viz :refer :all]
   ))

(defn system-graph
  ([system system-keys]
     (into {} (map
               #(let [system-re (map (fn [[k v]] [k v]) system)]
                  [% (if (map? (-> system %))
                       (into [] (mapcat
                                 (fn [v]
                                   (map first  (filter (fn [[k vv]] (= vv v)) system-re)))
                                 (filter (clojure.set/intersection (-> system vals set))
                                         (-> system % vals set))))
                       '[])])
               system-keys)))
  ([system ]
     (system-graph system (-> system keys))))


(defn view-system-image [yellow-set g]
  (view-graph (keys g) g
              :node->descriptor
              (fn [n] (merge {:label n
                             :style [:filled :rounded]
                             :shape :rect
                             }
                            (if (contains? yellow-set n)
                              {:fillcolor :yellow}
                              {:fillcolor :white})
                            ))
              ))

(defn save-system-image [yellow-set orange-set g]
  (save-graph (keys g) g
              :node->descriptor
              (fn [n] (merge {:label n
                              :style [:filled :rounded]
                              :shape :rect
                              }
                             (if (contains? yellow-set n)
                               {:fillcolor :yellow}
                               (if (contains? orange-set n)
                                 {:fillcolor :orange}
                                 {:fillcolor :white}))
                             ))
              :filename "rhizome.png"
              ))
