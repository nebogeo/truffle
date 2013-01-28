;; Copyright (C) 2012 FoAM vzw
;; This program is free software: you can redistribute it and/or modify
;; it under the terms of the GNU Affero General Public License as
;; published by the Free Software Foundation, either version 3 of the
;; License, or (at your option) any later version.
;;
;; This program is distributed in the hope that it will be useful,
;; but WITHOUT ANY WARRANTY; without even the implied warranty of
;; MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
;; GNU Affero General Public License for more details.
;;
;; You should have received a copy of the GNU Affero General Public License
;; along with this program.  If not, see <http://www.gnu.org/licenses/>.

(ns oak.ushahidi-plant
  (:use
   oak.vec2
   oak.forms
   oak.log
   oak.defs
   oak.profile
   oak.ushahidi
   oak.db))

(defn temp-tile-distance
  "calculate distance over different tiles"
  [tilea posa tileb posb]
  (let [diff (vec2-mul (vec2-sub tileb tilea) tile-size)]
    (vec2-dist posa (vec2-add diff posb))))


(defn make-ushahidi-plant [id tile name pos layer ush-id date lat lng fract incident]
  (hash-map
   :version 1
   :entity-type "ushahidi"
   :id id
   :tile tile
   :owner name
   :pos pos  
   :state "alive"
   :layer layer
   :time (current-time)
   :ush-id ush-id
   :date date
   :lat lat
   :lng lng
   :fract fract
   :incident incident
   :neighbours ()
   :power 0
   :thanks ()
   ))

(defn ushahidi-plant-count-fungi [neighbours]
  (reduce
   (fn [r entity]
     (if (= (:entity-type entity) "plant")
       (+ r 1) r))
   0
   neighbours))


(defn ushahidi-plant-add-neighbour [plant entity]
  (modify
   :neighbours (fn [n] (cons (:id entity) n)) plant))

(defn ushahidi-plant-thank [plant player-name fungi]
  (let [in (:incident (:incident plant))
        ush-id (:incidentid in)]
    (println "thanking" player-name (:id plant) (:id fungi))
    (modify :thanks
            (fn [t]
              (cons {:name player-name
                     :fungi-id (:id fungi)
                     :fungi-tile (:tile fungi)} t))
            plant)))
  
(defn ushahidi-plant-dothank [plant entity]
  (reduce
   (fn [plant player-name]
     (ushahidi-plant-thank plant player-name entity))
   (ushahidi-plant-add-neighbour plant entity)
   (:grown-by entity)))

(defn ushahidi-plant-dopower [plant]
  (modify :power (fn [p] (+ p 1)) plant))

(defn ushahidi-plant-update-neighbours [plant neighbours]
  (let [plant (modify :power (fn [p] 0) plant)]
    (reduce
     (fn [plant entity]
       (if (and (= (:entity-type entity) "plant")
                (< (temp-tile-distance (:tile plant) (:pos plant)
                                       (:tile entity) (:pos entity))
                   ushahidi-plant-influence-distance))
         (ushahidi-plant-dopower
          ;; only thank if we haven't seen it before
          (if (not (list-contains? (:neighbours plant) (:id entity)))
            (ushahidi-plant-dothank plant entity)
            plant))
         plant))
     plant
     neighbours)))

(defn ushahidi-plant-update [plant neighbours]
  (ushahidi-plant-update-neighbours plant neighbours))