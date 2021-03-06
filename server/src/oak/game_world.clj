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

(ns oak.game-world
  (:use
   oak.io
   oak.forms
   oak.vec2
   oak.plant
   oak.tile
   oak.rand
   oak.spirit
   oak.fruit
   oak.log
   oak.player
   oak.defs
   oak.db
   oak.profile
   oak.id-gen
   oak.avatar
   oak.ushahidi-plant
   oak.ushahidi
   oak.wormhole)
  (:require
   clojure.math.numeric-tower))

(defn game-world-get-tile
  "get a single tile from the game world"
  [game-world pos]
  (prof
   :get-tile
   (let [tiles (db-get :tiles {:pos.x (:x pos) :pos.y (:y pos)})]
     (if (not (empty? tiles)) (first tiles) false))))

(defn game-world-get-tile-with-neighbours
  "get a tile and it:s immediate neighbours"
  [game-world pos]
  (prof
   :get-tiles
   (db-get :tiles {:pos.x {:$lt (+ (:x pos) 2)  
                           :$gt (- (:x pos) 2)}
                   :pos.y {:$lt (+ (:y pos) 2)  
                           :$gt (- (:y pos) 2)}})))

(defn game-world-modify-tile
  "run function f on the tile at position pos,
   giving the tile as the only argument"
  [game-world pos f]
  (db-find-update! f :tiles {:pos.x (:x pos) :pos.y (:y pos)})
  game-world)

(defn game-world-clear-old-summons
  "clear out the old summons"
  [game-world time delta]
  (modify
   :summons
   (fn [summons]
     (reduce
      (fn [ret summon]
        (merge ret
         {(first summon) ; the name of the spirit
          (filter
           (fn [summon-location]
             (> (:expire-time summon-location) time))
           (second summon))})) ; the locations and expire times
      {} summons))
   game-world))

(defn game-world-add-summons [summons spirit-name tile-pos expire]
  (merge
   summons
   {spirit-name
    (if (contains? summons spirit-name)
      (cons {:expire-time (+ (current-time) expire) :pos tile-pos}
            (get summons spirit-name))
      (list {:expire-time (+ (current-time) expire) :pos tile-pos}))}))

(defn game-world-modify-spirit
  "modify a spirit from it's name"
  [game-world name f]
  (modify
   :spirits
   (fn [spirits]
     (map
      (fn [spirit]
        (if (= name (:name spirit))
          (f spirit) spirit))
      spirits))
   game-world))

; now controlled by fatima
(defn game-world-summon-spirit
  "move the spirit corresponding to this entities
  layer to a new tile, not guaranteed to work, as
  a random summons is honored"
  [world tile-pos entity expire]
  (let [spirit-name (layer->spirit-name (:layer entity))]
    ;(println "summoning" spirit-name)
    ;(println (:summons world))
    (game-world-modify-spirit
     (modify
      :summons
      (fn [s]
        (game-world-add-summons s spirit-name tile-pos expire))
      world)
     spirit-name ; clear the last message
     (fn [spirit] ; so we go to the right tile on the client
       (modify :last-message (fn [m] {}) spirit)))))

(defn game-world-add-tile
  "add a tile to the game (this is done gradually as people plant)
   leaving a sparse list"
  [game-world tile time delta]
  (db-add! :tiles tile)
  (game-world-modify-tile ; run update on the tile to init the plant
   game-world
   (:pos tile)
   (fn [tile]
     (tile-update tile time delta (:rules game-world)
                  (game-world-get-tile-with-neighbours
                    game-world (:pos tile))))))

(defn game-world-add-entity
  "add a new entity into the world, and summon the right
   spirit to come and look at it"
  [game-world tile-pos entity time delta]
   (let [tile (game-world-get-tile game-world tile-pos)]
    (game-world-summon-spirit
     (if (not tile)
       (game-world-add-tile game-world (make-tile tile-pos (list entity)) time delta)
       (game-world-modify-tile ; run update on the tile to init the plant
        game-world
        tile-pos
        (fn [tile]
          (tile-update (tile-add-entity tile entity)
                       time delta (:rules game-world)
                       (game-world-get-tile-with-neighbours
                         game-world (:pos tile))))))
     tile-pos
     entity
     player-summons-expire)))

(defn game-world-add-entity-overwrite
  "add a new entity into the world, and summon the right
   spirit to come and look at it"
  [game-world tile-pos entity time delta]
   (let [tile (game-world-get-tile game-world tile-pos)]
    (game-world-summon-spirit
     (if (not tile)
       (game-world-add-tile game-world (make-tile tile-pos (list entity)) time delta)
       (game-world-modify-tile ; run update on the tile to init the plant
        game-world
        tile-pos
        (fn [tile]
          (tile-update (tile-add-entity-no-check tile entity)
                       time delta (:rules game-world)
                       (game-world-get-tile-with-neighbours
                         game-world (:pos tile))))))
     tile-pos
     entity
     player-summons-expire)))


(defn make-game-world
  "make a world in the old-fashioned way for building a new
   database with"
  [num-plants area]
  (let [id-gen (make-id-generator)]
    (reduce
     (fn [world plant]
       (game-world-add-entity
        world
        (:tile plant)
        plant 0 1))
     (hash-map
      :version 1
      :log (make-log 100)
      :id-gen id-gen
      :spirits ()
      :wormholes ()
      :next-ushahidi 0
      :players (list (make-player 97 "Charlie" -1 (rand-nth avatar-types))
                     (make-player 98 "Percy" -1 (rand-nth avatar-types))
                     (make-player 99 "Alan" -1 (rand-nth avatar-types)))
      :summons {}
      :leaderboard ()
      :rules (load-companion-rules "rules.txt")
      :most-distant-info {:distance 0
                          :player ""
                          :tile-pos (make-vec2 0 0)
                          :pos (make-vec2 0 0)})
     (repeatedly 10
                 (fn []
                   (make-random-plant
                    (id-gen)
                    (make-vec2 0 1)
                    (make-vec2 (rand-int 5)
                               (rand-int 5))))))))

(defn game-world-find-player-id
  "get the player id from the name (should only be used when logging in)"
  [game-world name]
  (:id (first (db-get :players {:name name}))))

(defn game-world-find-player
  "find player by id"
  [game-world id]
  (first (db-get :players {:id id})))

(defn game-world-id->player-name [game-world id]
  "helper to get the name from a player id"
  (:name (game-world-find-player game-world id)))

(defn game-world-db-build!
  "build a database from the world"
  [game-world]
  (db-build-collection! :players (map
                                  (fn [player]
                                    (merge player
                                           {:flowered-plants ()}
                                           {:seeds-capacity 5}
                                           {:seeds-left 5}
                                           {:log (merge (:log player)
                                                        {:notes ()})}))
                                  (:players game-world)))
  (db-add-index! :players [:id])
  
  (db-build-collection! :tiles
                        (map
                         (fn [tile]
                           (merge
                            tile
                            {:index (str (:x (:pos tile)) ","
                                         (:y (:pos tile)))}))
                         (:tiles game-world)))

 ; todo - add new tick time to plants
  
  (db-add-index! :tiles [:index]))

(defn upgrade-plant-owner-names! [world]
  (db-map!
   (fn [tile]
     (modify
      :entities
      (fn [entities]
        (map
         (fn [plant]
           (let [owner-name (game-world-id->player-name
                             world (:owner-id plant))]
             (println "upgrading plant" (:id plant) "to include owner" owner-name)
             (merge plant {:owner owner-name})))
         entities))
      tile))
   :tiles)) 

(defn upgrade-entity-types! [world]
  (db-map!
   (fn [tile]
     (modify
      :entities
      (fn [entities]
        (map
         (fn [plant]
           (println "upgrading plant" (:id plant))
           (merge plant {:entity-type "plant"}))
         entities))
      tile))
   :tiles)
  (db-map!
   (fn [player]
     (println "upgrading" (:name player) "to include tile and avatar")
     
     ;; add avatar
     (let [tile (first (db-get-random-one :tiles {}))]
       (db-update!
        :tiles tile
        (merge tile {:entities
                     (cons (make-avatar
                            (:id player)
                            (:name player)
                            (make-vec2
                             (rand-int 5)
                             (rand-int 5))
                            (:layer player)
                            (count (:flowered-plants player)))
                           (:entities tile))}))
   
       (merge player {:tile (:pos tile)})))
   :players))

(defn upgrade-log-one-time-msgs! [world]
  ; logs on plants
  (db-map!
   (fn [tile]
     (modify
      :entities
      (fn [entities]
        (map
         (fn [plant]
           (modify
            :log
            (fn [log]
              (println "upgrading plant" (:id plant) "to include one-time msgs")
              (merge log {:one-time-msgs ()}))
            plant))
         entities))
      tile))
   :tiles)
  ; logs on players
  (db-map!
   (fn [player]
     (modify
      :log
      (fn [log]
        (println "upgrading player" (:name player) "to include one-time msgs")
        (merge log {:one-time-msgs ()}))
      player))
   :players))

(defn game-world-set-db-version! [world current to]
  (db-update!
   :game current
   (merge current {:value to})))

(defn game-world-upgrade-db! [world]
  (let [current-version (first (db-get :game {:name "version"}))]
    (when (or (not current-version)
              (> db-version (:value current-version)))
      (cond
       (= (:value current-version) 0)
       (do
         (upgrade-plant-owner-names! world)
         (upgrade-log-one-time-msgs! world)
         (game-world-set-db-version!
          world current-version 1)
         (game-world-upgrade-db! world))
       (= (:value current-version) 1)
       (do
         (upgrade-entity-types! world)
         (game-world-set-db-version!
          world current-version 2)
         (game-world-upgrade-db! world))))))

(defn make-empty-game-world
  "make a world for use with the database"
  []
  (let [id-gen (make-id-generator)
        world (hash-map
               :version 1
               :log (make-log 100)
               :id-gen id-gen
               :next-ushahidi 0
               :spirits ()
               :leaderboard ()
               :summons {}
               :wormholes ()
               :rules (load-companion-rules "rules.txt")
               :most-distant-info {:distance 0
                                   :player ""
                                   :tile-pos (make-vec2 0 0)
                                   :pos (make-vec2 0 0)})]
    (game-world-upgrade-db! world)
    world))

(defn game-world-save [game-world filename]
  (spit filename 
        (modify :id-gen
                (fn [id-gen]
                  (- (id-gen) 1)) ; save the current id
                game-world)))

(defn game-world-load [filename]
  (let [w (read-string (slurp filename))
        v (:version w)]
    (println (str "loading world version " v))
    (modify
     :id-gen
     (fn [id]
       (make-id-generator)) ; convert the id into the function
     w)))

(defn send-msg-to-twitter [msg]
  (spit 
   (str "broadcast/" (:from msg) "/msg")
   (str
    (:display msg) "\n"
    (:from msg) "\n"
    (:emotion msg) "\n"
    (:code msg) "\n"
    (count (:extra msg)) "\n"
    (apply
     str
     (map
      (fn [s]
        (str s "\n"))
      (:extra msg))))))

(defn game-world-process-msg
  "replace id numbers with strings for
   the client to make sense of - add information"
  [game-world msg]
  (let [extra (if (or
                   ; in these messages, we want to replace the id
                   ; of the other player in the extra field with
                   ; their name 
                   (= (:code msg) "your_plant_doesnt_like")
                   (= (:code msg) "i_am_detrimented_by")
                   (= (:code msg) "i_am_detrimental_to")
                   (= (:code msg) "i_am_benefitting_from")
                   (= (:code msg) "i_am_beneficial_to")
                   (= (:code msg) "needs_help")
                   (= (:code msg) "ive_asked_x_for_help")
                   (= (:code msg) "gift_sent") ; this msgs doesn't get here as sent by core
                   (= (:code msg) "gift_received") ; this msgs doesn't get here as sent by core
                   ;(= (:code msg) "thanks_for_helping")
                   )
                (do
                  ;(println "fixup for" (:code msg))
                  (cons ; add the name to the start of the list
                   (game-world-id->player-name
                    game-world (first (:extra msg)))
                   (:extra msg)))
                (:extra msg))]    
    (cond
     (= (:type msg) "plant")
     (merge
      msg
      {:display (game-world-id->player-name
                 game-world (:player msg))
       :owner (game-world-id->player-name
               game-world (:owner msg))
       :extra extra})
     
     (= (:type msg) "spirit")
     (let [ret (merge
                msg
                {:display (game-world-id->player-name
                           game-world (:player msg))
                 :extra extra})]
       (send-msg-to-twitter ret)
       ret)
           
     :else msg)))

(defn game-world-collect-all-msgs
  "get messages from all the tiles/plants and spirits"
  [game-world time]
  (prof
   :collect-all-messages
   (map
    (fn [msg]
      (game-world-process-msg game-world msg))
    (db-partial-reduce
     (fn [r tile]
       (let [msgs (concat r (tile-get-log tile))]
         (db-update! :tiles tile (tile-clear-log tile))
         msgs))
     (reduce
      (fn [r spirit]
        (concat r (:msgs (:log spirit))))
      ()
      (:spirits game-world))
     :tiles
     time
     server-db-items))))

(defn game-world-get-decayed-owners
  "find the owners of decayed plants so we can update the count"
  [game-world]
  (db-reduce
   (fn [r tile]
     (concat r (tile-get-decayed-owners tile)))
   ()
   :tiles))

(defn add-to-leaderboard [score lb]
  (cond
   (empty? lb) (list score)
   (= (:player (first lb)) (:player score)) (cons score (rest lb))
   :else (cons (first lb) (add-to-leaderboard score (rest lb)))))

(defn game-world-update-top-players
  "for the leaderboard"
  [game-world time]
  (modify
   :leaderboard
   (fn [scores]
     (let [lowest-score (if (empty? scores) 0 (:score (nth scores (- (count scores) 1))))
           unsorted (db-partial-reduce
                     (fn [scores player]
                       (if (and (> (:plant-count player) 0)
                                (or (< lowest-score (:plant-count player))
                                    (< (count scores) leaderboard-max)))
                         (add-to-leaderboard {:score (:plant-count player)
                                              :player (:name player)
                                              :helped (count (:has-picked player))} scores)
                         scores))
                     scores
                     :players
                     time
                     server-db-items)
           sorted (sort-by (fn [i] (:score i)) unsorted)]
       (discard (reverse sorted) leaderboard-max)))
   game-world))

; need to do this before tile update, when decayed plants
; are removed from the game
(defn game-world-update-player-plant-counts
  "update the plant counts for each player"
  [game-world]
  (let [decayed (game-world-get-decayed-owners game-world)]
    (db-map!
     (fn [player]
       (modify
        :plant-count
        (fn [c]
          (- c (count-items decayed (:id player))))
        player))
     :players))
  game-world)

(defn game-world-get-unique-oak-helpers [game-world time]
  (db-partial-reduce
   (fn [r tile]
     (reduce
      (fn [r entity]
        (if (and
             (= (:type entity) "oak")
             (= (:state entity) "fruit-c"))
          (do
            (println "found fruited oak")
            (reduce ; get a list of unique players
             (fn [r neighbour]
               (set-cons (:owner-id neighbour) r))
             r
             (tile-get-neighbours
              (:tile entity)
              (:id entity)
              (:pos entity)
              (game-world-get-tile-with-neighbours
                game-world (:tile entity)))))
          r))
      r
      (:entities tile)))
   ()
   :tiles
   time server-db-items))

(defn game-world-update-acorns!
  [game-world time]
  (doseq [id (game-world-get-unique-oak-helpers game-world time)]
    (db-find-update!
     (fn [player]
       (modify
        :acorns
        (fn [a]
          (println "updating" (:name player) "acorns to" (+ a 1))
          (+ a 1))
        player))
     :players
     {:id id})))

(defn game-world-update-tiles
  "update the tiles"
  [game-world time delta]
  (prof
   :update-tiles
   (do
     (db-partial-map!
      (fn [tile]
        (prof :tile-update
              (tile-update tile time delta (:rules game-world)
                           (game-world-get-tile-with-neighbours
                             game-world (:pos tile)))))
      :tiles
      time server-db-items) 
     (modify
      :rules ; load the rules each update so they can be changed
      (fn [r] ; easily while running
        (load-companion-rules "rules.txt"))
      game-world))))

(defn game-world-summon-to-ill-plants
  "collect list of ill plants worldwide and summon agents to some"
  [game-world time]
  ; don't do this all the time... (give em a chance)
  (if (< (rand-int 100) ill-summon-prob)
    (let [some-ill-plants
          (db-partial-reduce
           (fn [r tile]
             (reduce
              (fn [r plant]
                (if (or
                     (= (:state plant) "ill-a")
                     (= (:state plant) "ill-b")
                     (= (:state plant) "ill-c"))
                  (cons plant r) r))
              r
              (:entities tile)))
           ()
           :tiles
           time server-db-items)]
      (if (> (count some-ill-plants) 0)
        (let [chosen (rand-nth some-ill-plants)]
          (game-world-summon-spirit
           game-world (:tile chosen) chosen ill-plant-summons-expire))
        game-world))
    game-world))

(defn game-world-update-avatar-layer! [game-world player]
  (game-world-modify-tile
   game-world
   (:tile player)
   (fn [tile]
     (tile-modify-entity
      tile (:id player)
      (fn [avatar]
        (modify :layer (fn [l] (:layer player)) avatar))))))

(defn game-world-update-avatar-flowered! [game-world player]
  (game-world-modify-tile
   game-world
   (:tile player)
   (fn [tile]
     (tile-modify-entity
      tile (:id player)
      (fn [avatar]
        (let [c (count (:flowered-plants player))]
          (modify :flowered-plants (fn [f] c) avatar)))))))


(defn game-world-update-players
  "do things that need updating for players"
  [game-world time]
  (game-world-update-acorns! game-world time)
  (prof
   :update-players
   (db-partial-map! 
    (fn [player]
      (let [r (player-update player (:id-gen game-world))]
        (when (not (= (:layer player) (:layer r))) ; todo: check changed
          (game-world-update-avatar-layer! game-world r))
        (when (not (= (count (:flowered-plants player))
                      (count (:flowered-plants r))))
          (game-world-update-avatar-flowered! game-world r))
        r))
    :players
    time
    server-db-items))
  game-world)

(defn game-world-modify-player
  "replace the specified player with the result of f"
  [game-world id f]
  (db-find-update! f :players {:id id})
  game-world)

(defn game-world-login [game-world player-id]
  (game-world-modify-player
   game-world
   player-id
   (fn [player]
     (if (< (:version player) 2) ; do the upgrade
       (merge player {:version 2 
                      :next-refresh 0 ; fix time change
                      :last-login (current-time)}) ; add last login
       (modify
        :last-login
        (fn [ll] (current-time))
        player)))))

(defn game-world-move-player [game-world player-id tile-pos pos]
  (let [player (game-world-find-player game-world player-id)
        old-tile-pos (:tile player)]

    ; add a tile if it's not there already
    (let [tile (game-world-get-tile game-world tile-pos)]
      (when (not tile)
        (db-add! :tiles (make-tile tile-pos (list)))))

    ; modify the tile
    (game-world-modify-tile
     game-world
     tile-pos
     (fn [tile]
       ; are we there already?
       (if (tile-find-entity tile player-id)
         (tile-modify-entity ; then move
          tile player-id
          (fn [avatar]
            (modify :time (fn [t] (current-time))
                    (modify :pos (fn [p] pos) avatar))))
         ; make a new avatar
         (merge tile {:entities (cons (make-avatar player-id
                                                   (:name player)
                                                   pos
                                                   (:layer player)
                                                   (count (:flowered-plants player))
                                                   (:avatar-type player))
                                      (:entities tile))}))))

    ; have we changed tiles?
    (when (not (vec2-eq? tile-pos old-tile-pos))
      ; remove from old tile
      (game-world-modify-tile
       game-world
       old-tile-pos
       (fn [old-tile]
         (tile-remove-entity old-tile player-id))))
        
    (game-world-modify-player
     game-world
     player-id
     ; update tile pos in player so we can find the avatar
     (fn [player]
       (modify
        :tile
        (fn [p]
          tile-pos)
        player)))

    game-world))

(defn game-world-chat [game-world text player-id tile-pos]
  (let [player (game-world-find-player game-world player-id)]
    (game-world-modify-tile
     game-world
     tile-pos
     (fn [tile]
       (tile-modify-entity 
        tile player-id
        (fn [avatar]
          (modify :chat (fn [c] text)
                  avatar)))))))


(defn game-world-post-logs-to-players
  "dispatch messages to the players"
  [game-world msgs]
  ; reduce over messages and send to players
  (reduce
   (fn [world msg]
     (if (or
          (= (:type msg) "plant")
          (= (:code msg) "needs_help"))
       (game-world-modify-player
        world (:player msg)
        (fn [player]
          (modify
           :log
           (fn [log]
             (log-add-msg-ignore-one-time log msg))
           player)))
       world))
   game-world
   msgs))
           
(comment
  (prof
   :post-logs-map!
   (db-map!
    (fn [player]
      (modify
       :log
       (fn [log]
         (reduce
          (fn [log msg]
            (if (= (:player msg) (:id player))
              (log-add-msg-ignore-one-time log msg)
              log))
          log
          msgs))
       player))
    :players))
  game-world)

(defn game-world-cull-empty-tiles [game-world]
  (let [items (db-get-random-one :tiles {:entities ()})]
    (when (> (count items) 0)
      (println "destroying tile" (:index (first items)))
      (db-destroy! :tiles (first items))))
  game-world)

(defn game-world-distance [tile-pos pos]
  (let [wp (make-vec2 (+ (* (:x tile-pos) 5) (:x pos))
                      (+ (* (:y tile-pos) 5) (:y pos)))]
    (if (or (< (:x wp) 0) (> (:x wp) 0)
            (< (:y wp) 0) (> (:y wp) 0))
      (vec2-mag wp)
      0)))

(defn game-world-update-distance-info [game-world time]
  (db-partial-reduce
   (fn [gw tile]
     (reduce
      (fn [gw entity]
        (if (= (:entity-type entity) "plant")
          (let [distance (game-world-distance (:pos tile)
                                              (:pos entity))]
            ;;(println distance)
            ;;(println (:most-distant-info gw))
            (if (> distance (:distance (:most-distant-info gw)))
              (modify
               :most-distant-info
               (fn [di]
                 {:distance distance
                  :player (first (:grown-by entity))
                  :tile-pos (:pos tile)
                  :pos (:pos entity)})
               gw)
              gw))
          gw))
      gw
      (:entities tile)))
   game-world
   :tiles
   time
   server-db-items))

;; !! won't work for offesets greater than one tile
(defn game-world-calc-world-pos [tilepos pos offset size]
  (let [new-pos (vec2-add pos offset)]
    {:tile-pos (vec2-add tilepos (make-vec2
                                  (cond (< (:x new-pos) 0) -1
                                        (>= (:x new-pos) (:x size)) 1
                                        :else 0)
                                  (cond (< (:y new-pos) 0) -1
                                        (>= (:y new-pos) (:y size)) 1
                                        :else 0)))
     :pos (vec2-mod new-pos size)}))

(defn game-world-make-rnd-pos-list []
  (repeatedly
   spore-count
   (fn [] {:x (- (rand-int 3) 1)
           :y (- (rand-int 3) 1)})))

(defn fungi-add-powering! [fungi-id fungi-tile plant]
  (db-find-update!
   (fn [tile]
     (modify
      :entities
      (fn [entities]
        (map
         (fn [entity]
           (if (= (:id entity) fungi-id)
             (do
               (println "telling fungi it is helpful")
               (modify :powering
                       (fn [p] 
                         (cons {:id (:id plant)
                                :tile (:tile plant)
                                :pos (:pos plant)} p))
                       entity))
             entity))
         entities))
      tile))
   :tiles
   {:pos.x (:x fungi-tile)
    :pos.y (:y fungi-tile)}))
  
(defn inc-helpful-plant-count! [player-name ush-id]
  (db-find-update!
   (fn [player]
     (println (:name player) "score is increasing")
     (modify :plant-count (fn [c] (+ c 1))
             (modify :has-picked (fn [p] (set-cons ush-id p))
                     player)))
   :players
   {:name player-name}))

(defn game-world-thank [game-world time]
  ;; do the imperative bit
  (db-partial-reduce
   (fn [l tile]
     (doseq [entity (:entities tile)]
       (when (and (= (:entity-type entity) "ushahidi")
                  (not (empty? (:thanks entity))))
         (doseq [thank (:thanks entity)]
           (let [ush-id (:ush-id entity)
                 player-name (:name thank)]
             (println "found thanks on " ush-id " for " player-name)
             ;; tell fungi we are being helpful 
             (fungi-add-powering!
              (:fungi-id thank)
              (:fungi-tile thank)
              entity)
              ;; increment plant count for this player
              (inc-helpful-plant-count! player-name (:id entity))
              ;; send comment to zizim site
              (ushahidi-add-incident-comment
               ush-id player-name
               (str player-name " has helped this plant in aniziz"))))))
     l)
   '()
   :tiles
   time
   server-db-items)

  ;; clear thanks
  (db-partial-map!
   (fn [tile]
     (modify
      :entities
      (fn [entities]
        (map
         (fn [entity]
           (modify :thanks (fn [t] []) entity))
         entities))
      tile))
   :tiles
   time
   server-db-items)
  
  game-world)

(defn game-world-spore [game-world time delta]
  (db-partial-reduce
   (fn [gw tile]
     (reduce
      (fn [gw entity]
        (if (= (:state entity) "spore")
          (reduce
           (fn [gw pos]
             (let [world-pos (game-world-calc-world-pos
                              (:tile entity)
                              (:pos entity)
                              pos
                              (make-vec2 tile-size tile-size))]
               (game-world-add-entity
                gw
                (:tile-pos world-pos)
                (modify
                 :grown-by
                 (fn [gb] (list (first (:grown-by entity))))
                 (make-plant
                  ((:id-gen game-world))
                  (:tile-pos world-pos)
                  (:pos world-pos)
                  (:type entity)
                  (:owner entity)
                  (:owner-id entity)
                  (:soil entity)))
                time delta)))
           gw
           (game-world-make-rnd-pos-list))
          gw))
      gw
      (:entities tile)))
   game-world
   :tiles
   time
   server-db-items))

(defn game-world-type-in-tile [game-world tile-pos type]
  (let [tile (game-world-get-tile game-world tile-pos)]
    (if tile
      (tile-get-type tile type)
      false)))


;; wont differentiate against avatars...
(defn game-world-other-plant-here [game-world tile-pos pos]
  (let [tile (game-world-get-tile game-world tile-pos)]
    (if tile
      (tile-position-taken? tile pos)
      false)))

(defn get-layer-from-categories [cats]
  (reduce
   (fn [r cat]
     (let [layer (db-cat->layer (:category_title (:category cat)))]
       (if (and (= r "none") (not (= layer "none")))
         layer r)))
   "none"
   cats))

(defn game-world-add-wormhole [game-world id p]
  (modify
   :wormholes
   (fn [wh] (set-cons (merge p {:id id}) wh))
   game-world))

(defn game-world-get-random-wormhole-coords [game-world id]
  (rand-nth (filter
             (fn [wh] (not (= (:id wh) id)))
             (:wormholes game-world))))
                                                     
(defn game-world-update-ushahidi [game-world time delta]
  (if (> time (:next-ushahidi game-world))
    (do
      (reduce
       (fn [gw incident]
         (let [in (:incident incident)
               p (ushahidi-incident->pos incident)
               ush-id (:incidentid in)
               cats (:categories (:payload (ushahidi-get-categories ush-id)))
               layer (get-layer-from-categories cats)]
           ;(println cats)
           ; TODO split this thing up
           (if (= layer "wormhole")
             (let [wh (game-world-type-in-tile gw (:tile-pos p) "wormhole")]
               (if (not wh)
                 (let [id ((:id-gen gw))]
                   (println "adding wormhole!"
                            (:tile-pos p) (:pos p)
                            (:locationlatitude in)
                            (:locationlongitude in))
                                        ;                (println in)
                   (game-world-add-entity-overwrite
                    (game-world-add-wormhole gw id p)
                    (:tile-pos p)
                    (make-wormhole
                     id
                     (:tile-pos p)
                     (:pos p)
                     (:fract p))
                    time
                    delta))
                 (game-world-add-wormhole gw (:id wh) p)))
             (if (and
                  (not (= layer "none"))
                  (not (game-world-other-plant-here
                        gw
                        (:tile-pos p) (:pos p))))
               (do
                 (println "adding boskoi plant!"
                          (:tile-pos p) (:pos p)
                          (:locationlatitude in)
                          (:locationlongitude in))
                                        ;                   (println in)
                 (game-world-add-entity
                  gw
                  (:tile-pos p)
                  (make-ushahidi-plant
                   ((:id-gen gw))
                   (:tile-pos p)
                   "ushahidi"
                   (:pos p)
                   layer
                   ush-id
                   (:incidentdate in)
                   (Float/parseFloat (:locationlatitude in))
                   (Float/parseFloat (:locationlongitude in))
                   (:fract p)
                   in)
                  time
                  delta))
               gw))))
       (modify :next-ushahidi (fn [n] (+ time 30)) game-world)
       (ushahidi-get-incidents-since 0)))
    game-world))

(defn game-world-update
  "main update"
  [game-world time delta]
  (let [msgs (game-world-collect-all-msgs game-world time)]
    (modify :log (fn [log]
                   (reduce
                    (fn [log msg]
                      (log-add-msg log msg))
                    log
                    msgs))
             (game-world-update-top-players
              (game-world-update-distance-info
               (game-world-cull-empty-tiles
                (game-world-update-ushahidi
                 (game-world-update-players
                  (game-world-summon-to-ill-plants
                   (game-world-spore
                    (game-world-update-tiles
                     (game-world-post-logs-to-players
                      (game-world-clear-old-summons
                       (game-world-thank
                        game-world time) time delta)
                      msgs) time delta) time delta) time) time)
                 time delta)) time)
              time))))

(defn game-world-find-spirit
  "get the spirit from it's name"
  [world name]
  (reduce
   (fn [r spirit]
     (if (and (not r) (= name (:name spirit)))
       spirit r))
   false
   (:spirits world)))

(defn game-world-add-player
  "make a new player"
  [game-world name fbid]
  (let [player-id ((:id-gen game-world))]
    (db-add! :players
             (make-player player-id name fbid (rand-nth avatar-types)))
    ; todo: perhaps scatter a bit?
    (game-world-move-player game-world player-id (make-vec2 0 0) (make-vec2 2 2))))

(defn game-world-find-plant
  "find a plant from it's tile pos and id"
  [game-world tile-pos plant-id]
  (tile-find-entity
   (game-world-get-tile game-world tile-pos)
   plant-id))
  
(defn game-world-can-player-pick?
  "can the player pick from a plant?"
  [game-world player-id]
  (let [player (game-world-find-player game-world player-id)]
    true))
    ;(and (< (count (:seeds player)) 5)
    ;     (> (:seeds-left player) 0))))

(defn game-world-player-pick
  "pick fruit from a plant"
  [game-world player-id tile-pos plant-id]
  (game-world-modify-player
   game-world
   player-id
   (fn [player]
     (modify
      :seeds
      (fn [seeds]
        (let [plant (game-world-find-plant
                     game-world
                     tile-pos
                     plant-id)]
          (max-cons (make-fruit
                     ((:id-gen game-world))
                     (:type plant) (:layer plant))
                    seeds max-player-fruit)))
      (modify ; will have checked > 0 already
       :seeds-left (fn [s] s) ; (- s 1))
       player)))))

(defn game-world-populate-oaks [game-world num-oaks area time delta]
  (comment reduce
   (fn [game-world plant]
     (game-world-add-entity
      game-world
      (:tile plant)
      plant
      time
      delta))
   game-world
   (repeatedly
    num-oaks
    (fn []
      (merge
       (make-random-plant 
        ((:id-gen game-world))
        (make-vec2
         (Math/round (* (rand-gaussian) area))
         (Math/round (* (rand-gaussian) area))))
       {:type "oak"
        :layer "tree"
        :owner-id 103249
        :owner "AncientGardener"
        :size (rand-int 500)}))))
  game-world)

