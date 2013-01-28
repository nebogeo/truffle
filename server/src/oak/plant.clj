;; Copyright (C) 2010 FoAM vzw
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

(ns oak.plant
  (:use
   oak.vec2
   oak.forms
   oak.log
   oak.defs
   oak.profile))

(defn make-plant [id tile pos type owner owner-id soil]
  (hash-map
   :version 2 ; check make random plant below
   :entity-type "plant"
   :id id
   :tile tile
   :pos pos
   :type type
   :layer (plant-type->layer type)
   :state "planted"
   :picked-by-ids ()
   :grown-by (list "gaia")
   :owner-id owner-id
   :owner owner
   :size 0
   :soil soil
   :timer 9999 ; force a tick when created
   :tick (+ plant-tick (Math/floor (rand plant-tick-var)))
   :health start-health
   :fruit 0
   :powering () ;; list of locations of plants we are powering
   :event-occurred ()
   :log (make-log 10)))

(defn plant-strip
  "remove crud that the client doesn't need - temporary measure"
  [plant player-layer]
  (let [stripped (select-keys
                  plant
                  [:entity-type :version :id :state :type :layer :pos :fruit :owner-id :owner :size :powering])]
    ; remove the fruit if the player hasn't reached the level yet
    (if (or (= player-layer "all")
            (= player-layer (:layer plant)))
      stripped
      (merge stripped {:fruit 0}))))

(defn plant-count [plant]
  (println (str "picked-by: " (count (:picked-by-ids plant)))))

(defn make-random-plant [id tile pos]
  (let [type (rand-nth tneg-types)
        owner (rand-nth
               (list (list 97 "Charlie")
                     (list 98 "Percy")
                     (list 99 "Alan")))]
    ; fudge to cause soil input from client
    (merge
     (dissoc
      (make-plant
       id
       tile
       (make-vec2 (Math/floor (rand tile-size))
                  (Math/floor (rand tile-size)))
       type
       (second owner)
       (first owner)
       0)
      :soil)
     {:version 1})))

(defn ill-slow []
  (= (rand-int ill-slow-amount) 0))

(defn growing-season [season]
  (or (= season "spring")
      (= season "summer")))

(defn can-grow [state]
  (or (= state "grow-a-ready")
      (= state "grow-b-ready")
      (= state "grow-c-ready")
      (= state "spore-ready")))

(defn plant-can-grow? [plant]
  (can-grow (:state plant)))
      
(defn adv-user [state]
  (cond
   (= state "grow-a-ready") "grow-b"
   (= state "grow-b-ready") "grow-c"
   (= state "grow-c-ready") "grow-d"
   (= state "spore-ready") "spore"
   :else state))

(defn adv-state
  "the plant state machine, advance state, based on health and season"
  [state health season layer type num-neighbours]
  ;(println state num-neighbours)
  (cond
   (= state "planted") "grow-a-ready"
   (= state "grow-b") "grow-b-ready"
   (= state "grow-c") "grow-c-ready"
   (= state "grow-d") (if (and (< num-neighbours max-neighbours)
                               (> num-neighbours min-neighbours))
                        "spore-ready"
                        "decay-a")
   (= state "spore") "decay-a"
   (= state "decay-a") "decay-b"
   (= state "decay-b") "decay-c"
   (= state "decay-c") "decay-d"
   (= state "decay-d") "decayed"
   (= state "decayed") "decayed"
   :else state))

(defn load-companion-rules [filename]
  (read-string (slurp filename)))

(defn get-relationship [from to rules]
  (nth (nth rules (plant-type->id from))
       (plant-type->id to)))

(defn plant-add-to-log
  "helper to add a message to a plant:s log"
  [plant log type]
  (log-add-msg 
   log
   (make-plant-msg type plant (:owner-id plant) ())))

(defn plant-add-to-log-extra
  "helper to add a message to a plant:s log"
  [plant log type extra]
  (log-add-msg 
   log
   (make-plant-msg type plant (:owner-id plant) extra)))

(defn plant-clear-log
  "clear the things needed before an update"
  [plant]
  (modify
   :log
   (fn [log]
     (merge log {:msgs ()})) ; don't want to clear the one time list
   plant))

(defn plant-clear-events
  "clear the things needed before an update"
  [plant]
  (modify
   :event-occurred
   (fn [ev] ())
   plant))

(defn neighbours-relationship
  "look for neighbours and see if we will help or hinder
   them from recovering - return list of plants based on comp fn"
  [plant neighbours rules comp]
  (filter
   (fn [other]
     (comp (get-relationship (:type plant) (:type other) rules) 0))
   neighbours))


(defn ill-neighbours-relationship
  "look for ill neighbours and see if we will help or hinder
   them from recovering - return list of plants based on comp fn"
  [plant neighbours rules comp]
  (filter
   (fn [other]
     (and
      (or
       (= (:state other) "ill-a")
       (= (:state other) "ill-b")
       (= (:state other) "ill-c"))
      (comp (get-relationship (:type plant) (:type other) rules) 0)))
   neighbours))

(defn log-relationship
  "sends a message to both sides of the relationship"
  [log plant plants to-me to-other]
  (reduce
   (fn [log other]
     (log-add-msg 
      (log-add-msg 
       log
       (make-plant-msg ; to them
        to-other other (:owner-id other)
        (list (:owner-id plant) (:type plant))))
      (make-plant-msg ; to us
       to-me plant (:owner-id plant)
       (list (:owner-id other) (:type other)))))
   log
   plants))

(defn log-thank-owners
  "send message to owners of all plants
   thanking them for recovery"
  [log plant helpful-neighbours]
  (reduce
   (fn [log other]
     (log-add-msg 
      log
      (make-plant-msg
       "thanks_for_helping"
       plant (:owner-id other)
       (list (:type other)))))
   log
   helpful-neighbours))

(defn plant-update-log
  "adds messages to the log depending on changing state"
  [plant old-state neighbours rules]    
    (modify
     :log
     (fn [log]
       (cond
        
        ; when first planted, need to inform owners of plants
        ; around us about our relationship with them
        (and (= old-state "planted")
             (= (:state plant) "grow-a"))
        (do
          (plant-add-to-log
           plant
           (log-relationship
            (log-relationship
             log plant
             (ill-neighbours-relationship plant neighbours rules >)
             "i_am_beneficial_to"
             "i_am_benefitting_from")
            plant
            (ill-neighbours-relationship plant neighbours rules <)
            "i_am_detrimental_to"
            "i_am_detrimented_by")
           "i_have_been_planted"))
          
        (and
         (not (= old-state "ill-a"))
         (not (= old-state "ill-b"))
         (= (:state plant) "ill-a"))
        (plant-add-to-log plant log "i_am_ill")
        
       ; (and (= old-state "decay-c")
       ;      (= (:state plant) "grow-a"))
       ; (plant-add-to-log plant log "i_am_regrowing")
        
        (and (= old-state "ill-c")
             (= (:state plant) "decayed"))
        (plant-add-to-log plant log "i_have_died")

        (and (= old-state "ill-a")
             (= (:state plant) "grown"))
        (log-thank-owners
         (plant-add-to-log plant log "i_have_recovered")
         plant
         (neighbours-relationship plant neighbours rules >))

        (and (not (= old-state "fruit-a"))
             (= (:state plant) "fruit-a"))
        ; we use this message to count flowered plants
        ; so add a little extra detail we need
        (plant-add-to-log-extra plant log "one_time_i_have_flowered"
                                (list (:id plant))) 

        (or
         (and
          (= old-state "ill-c")
          (= (:state plant) "ill-b"))
         (and
          (= old-state "ill-b")
          (= (:state plant) "ill-a")))
        (plant-add-to-log plant log "i_am_recovering")
        
;        (and (not (= old-state :fruit-a))
;             (= (:state plant) :fruit-a))
;        (plant-add-to-log plant log "i_have_fruited")
      
        :else log))
     plant))

(defn events-from-relationship
  "append new relationship events onto the list"
  [events-list plant type plants]
  (reduce
   (fn [r other]
     (max-cons (str (:layer other) "-" type "#" (:id plant)) r max-plant-events))
   events-list
   plants))

(defn plant-first-flower? [plant old-plant]
  (and
   (not (log-contains-msg?
         (:log old-plant)
         "one_time_i_have_flowered"))
   (log-contains-msg?
    (:log plant)
    "one_time_i_have_flowered")))
  
(defn plant-update-events [plant old-plant neighbours rules]    
  "add any special events that we need FAtiMA to be aware of"
  (let [old-state (:state old-plant)]
    (prof :plant-update-events
    (modify
     :event-occurred
     (fn [ev]
       (cond
        ; when first planted, need to tell fatima about
        ; our relationships with the plants around us
        (and (= old-state "planted")
             (= (:state plant) "grow-a"))
        (events-from-relationship
         (events-from-relationship
          ev plant "benefit"
          (neighbours-relationship plant neighbours rules >))
         plant "detriment"
         (neighbours-relationship plant neighbours rules <))
        
        (and
         (= old-state "ill-c")
         (= (:state plant) "ill-b"))
        (max-cons (str (:layer plant) "-recovery-to-b#" (:id plant)) ev max-plant-events)
        
        (and
         (= old-state "ill-b")
         (= (:state plant) "ill-a"))
        (max-cons (str (:layer plant) "-recovery-to-a#" (:id plant)) ev max-plant-events)
        
        (and (= old-state "ill-a")
             (= (:state plant) "grown"))
        (max-cons (str (:layer plant) "-finished-recovery#" (:id plant)) ev max-plant-events)

        (> (:fruit plant)
           (:fruit old-plant))
        (max-cons (str (:layer plant) "-new-fruit#" (:id plant)) ev max-plant-events)

        (plant-first-flower? plant old-plant) 
        (do
          (println "first flower")
          (max-cons (str (:layer plant) "-first-flower#" (:id plant)) ev max-plant-events)
          )
        :else ev))
     plant))))
  
(defn plant-update-from-changes
  "update the log and event-occurred from the
   current state and the last"
  [plant old-plant neighbours rules]
  (prof :plant-update-from-changes
        ; only need to carry on if something has changed
        (if (or
             (not (= (:state plant) (:state old-plant)))
             (not (= (:fruit plant) (:fruit old-plant)))
             (plant-first-flower? plant old-plant))        
          ; update the log
          (plant-update-log
           (plant-update-events
            plant old-plant neighbours rules)
           (:state old-plant) neighbours rules)
          plant)))

(defn count-neighbour-owners [neighbours]
  (count
   (reduce
    (fn [r neighbour]
      (if (list-contains? r (:owner neighbour))
        r (cons (:owner neighbour) r)))
    ()
    neighbours)))

(defn plant-user-grow [player plant]
  (modify :state (fn [state] (adv-user state))
          (modify :grown-by
                  (fn [gb]
                    (cons (:name player) gb))
                  plant)))

(defn plant-update-state [plant time delta season num-neighbours]
  (prof
   :plant-update-state
   (modify
   :timer
   (fn [timer]
     (+ timer delta))
   (if (> (:timer plant) (:tick plant))
     (modify
      :state
      (fn [state]
        (if (not (= (:type plant) "oak"))
          (adv-state state
                     (:health plant)
                     season
                     (:layer plant)
                     (:type plant)
                     num-neighbours)
          state))
      (modify
       :timer (fn [t] 0) plant))
     plant))))

(defn plant-update [plant time delta neighbours rules season]
  ;(println (str season " " (:state plant) " " (:health plant) " " (:timer plant) " " (:tick plant)))
  (let [old-plant plant]
    (plant-update-from-changes
     (plant-update-state
      plant
      time delta season
      (count neighbours))
     old-plant neighbours rules)))

(defn plant-diagnose
 "returns a list containing:
  { :needed_plants ( type-name1 type-name2 ... )
    :harmful_plants ( plant1 plant2 ... ) }"
  [plant neighbours rules]
  ; simply too many plants around?
  (if (> (count neighbours) max-neighbours)
    {:crowded true }
    {
     :crowded false
     :harmful_plants
     (filter
      (fn [n]
        (< (get-relationship (:type plant) (:type n) rules) 0))
      neighbours)
     :needed_plants
     (reduce
      (fn [r i]
        (if (> (first i) 0)
          (cons (second i) r)
          r))
      '()
      (map
       (fn [v t]
         ; don't want to suggest fungi here
         (if (= (plant-type->layer t) "fungi")
           (list 0 t)
           (list v t)))
       (nth rules (plant-type->id (:type plant)))
       plant-types))
     }))

(defn plant-picked
  "send a message to our owner when we have been picked"
  [plant player]
  (modify
   :log
   (fn [log]
     (log-add-msg 
      log
      (make-plant-msg
       "i_have_been_picked_by"
       plant (:owner-id plant)
       (list (:name player)))))
   (modify :fruit (fn [f] (- f 1))
           (modify ; add an event on
            :event-occurred
            (fn [ev]
              (cons (str (:layer plant) "-picked-fruit#"
                         (:id plant)) ev))
            plant))))