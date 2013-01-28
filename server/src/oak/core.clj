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

(ns oak.core
  (:import
   java.util.concurrent.Executors
   [org.webbitserver WebServer WebServers WebSocketHandler]
   [org.webbitserver.handler StaticFileHandler])
  (:require [clj-http.client :as client])
  (:use
   [clojure.data.json :only (read-json json-str)]
   oak.fatima-world
   oak.fatima-bridge
   oak.remote-agent
   oak.io
   oak.island
   oak.game-world
   oak.vec2
   oak.plant
   oak.tile
   oak.forms
   oak.player
   oak.logging
   oak.db
   oak.defs
   oak.profile
   oak.log
   oak.game-data
   oak.id-gen
   oak.avatar))

(check-&-build-id-gen)     

; ****************************************************************
; Uncomment the two lines below and run once to create a new world
; ****************************************************************
(def my-game-world (ref (make-game-world 200 2)))
(game-world-db-build! (sym-replace2 (deref my-game-world)))

(def my-game-world (ref (make-empty-game-world)))
;(game-world-populate-oaks (deref my-game-world) 100 10 0 1)


(def fatima-world
     (ref
      (make-world
       46875
       "data/characters/minds/language/agent/en/language-set-1"
       "data/characters/minds/Actions.xml"
       (list))))

(defn run []
  (let [time (current-time)]
    (dosync (ref-set fatima-world
                     (doall-recur
                      (world-run
                       (game-world-sync->fatima
                        (deref fatima-world)
                        (deref my-game-world)
                        time) time))))
    
    (let [new-world (doall-recur
                      (game-world-update
                       (game-world-sync<-fatima
                        (deref my-game-world)
                        (deref fatima-world))
                       time server-tick))]
      (dosync (ref-set my-game-world new-world)))))

(defn tick []
  (Thread/sleep (* server-tick 1000))
  (try
    (profile (run))
    (catch Exception e
      (println "Oops ... an error ocurred.")
      (.printStackTrace e))
    (finally))
  (recur))

(defn json [thing]
  (json-str (remove-ids thing)))

; server interface

(defn s-login [name fbid iefix]
  (let [id (game-world-find-player-id
            (deref my-game-world) name)]
    (cond
     (not id)
     (do
       (dosync
        (ref-set my-game-world
                 (game-world-add-player
                  (deref my-game-world) name fbid)))
       (let [player-id (game-world-find-player-id
                        (deref my-game-world) name)
             player (game-world-find-player
                     (deref my-game-world) player-id)]
         (dosync
          (ref-set
           my-game-world
           (game-world-login (deref my-game-world) player-id)))
         (game-log player "registered")
         (json {:player player :status "registered"})))
     :else
     (let [player (game-world-find-player
                   (deref my-game-world) id)]
       (dosync
        (ref-set
         my-game-world
         (game-world-login (deref my-game-world) id)))
       (game-log player "login")
       (json {:player player :status "login"})))))

(defn s-move-player [player-id tilex tiley posx posy iefix]
  (let [tile-pos (make-vec2 (parse-number tilex) (parse-number tiley))
        pos (make-vec2 (parse-number posx) (parse-number posy))
        player-id (parse-number player-id)]
    (dosync
     (ref-set my-game-world
              (game-world-move-player
               (deref my-game-world) player-id tile-pos pos)))
    (json '("ok"))))

(defn s-chat [player-id text iefix]
  (let [player-id (parse-number player-id)
        player (game-world-find-player
                (deref my-game-world)
                player-id)
        tile-pos (:tile player)]
    (println "chat" text)
    (dosync
     (ref-set my-game-world
              (game-world-chat
               (deref my-game-world) text player-id tile-pos)))
    (json '("ok"))))

(defn s-pull [player-id tilex tiley iefix]
  (let [tiles (game-world-get-tile-with-neighbours
                (deref my-game-world)
                (make-vec2 (parse-number tilex)
                           (parse-number tiley)))
        player (game-world-find-player
                (deref my-game-world)
                (parse-number player-id))
        player-layer (player-get-allowed-layer player)]
    (json
     {:player player
      :tiles (if player
               (map
                (fn [tile]
                                        ; take off the fruit we are not allowed to pick
                  (tile-strip tile player-layer player-id))
                tiles)
               tiles)
      :spirits
      (:spirits (deref my-game-world))
      :most-distant-info
      (:most-distant-info (deref my-game-world))
      :leaderboard
      (:leaderboard (deref my-game-world))})))

(defn s-get-msgs [id iefix]
  (let [id (parse-number id)]
    (if (< id 1)
      (json (:msgs (:log (deref my-game-world))))
      (do
        (let [player (game-world-find-player
                      (deref my-game-world) id)]
          (if player
            (json (:msgs (:log player)))
            (json {:error (str "no player " id " found")})))))))

(defn s-make-plant [tilex tiley posx posy type owner-id soil fruit-id iefix]
  (let [tile-pos (make-vec2 (parse-number tilex) (parse-number tiley))
        pos (make-vec2 (parse-number posx) (parse-number posy))
        owner-id (parse-number owner-id)
        fruit-id (parse-number fruit-id)
        player (game-world-find-player
                (deref my-game-world) owner-id)]
    (game-log player "planted" (make-location tile-pos pos) (list type))
    (if (player-has-fruit? player fruit-id)
      (dosync
       (ref-set my-game-world
                (game-world-modify-player
                 (game-world-add-entity
                  (deref my-game-world)
                  tile-pos
                  (make-plant
                   ((:id-gen (deref my-game-world)))
                   tile-pos pos type (:name player) owner-id (parse-number soil))
                  (current-time)
                  server-tick)
                 owner-id
                 (fn [player]
                   (player-inc-plant-count
                    (player-remove-fruit player fruit-id))))))
      (game-log player "plant-error!"))
    (json '("ok"))))

(defn s-grow [tilex tiley plant-id player-id iefix]
  (println "grow")
  (let [player-id (parse-number player-id)
        tile-pos (make-vec2 (parse-number tilex) (parse-number tiley))
        plant-id (parse-number plant-id)
        plant (game-world-find-plant
               (deref my-game-world) tile-pos plant-id)
        player (game-world-find-player
                (deref my-game-world) player-id)]
    
    (game-log player "grow" (make-location tile-pos (:pos plant))
              (list (:type plant)
                    (game-world-id->player-name
                     (deref my-game-world)
                     (:owner-id plant))))
    (dosync
     (ref-set my-game-world
              (game-world-modify-tile
               (deref my-game-world)
               tile-pos
               (fn [tile]
                 (tile-modify-entity
                  tile plant-id
                  (fn [plant]
                    (plant-user-grow player plant)))))))
    (json {:ok true})))

(defn s-pick [tilex tiley plant-id player-id iefix]
  (let [player-id (parse-number player-id)
        tile-pos (make-vec2 (parse-number tilex) (parse-number tiley))
        plant-id (parse-number plant-id)
        plant (game-world-find-plant
               (deref my-game-world) tile-pos plant-id)
        player (game-world-find-player
                (deref my-game-world) player-id)]
    
    (if (game-world-can-player-pick?
         (deref my-game-world) player-id)
      (do
        (game-log player "pick" (make-location tile-pos (:pos plant))
                  (list (:type plant)
                        (game-world-id->player-name
                         (deref my-game-world)
                         (:owner-id plant))))
        (dosync
         (ref-set my-game-world
                  (game-world-modify-tile
                   (game-world-player-pick
                    (deref my-game-world)
                    player-id tile-pos plant-id)
                   tile-pos
                   (fn [tile]
                     (tile-modify-entity
                      tile plant-id
                      (fn [plant]
                        (plant-picked
                         plant
                         (game-world-find-player
                               (deref my-game-world)
                               player-id))))))))
        (json {:ok true})) 
      (json {:ok false}))))

(defn s-soil [tilex tiley plant-id soil iefix]
  (let [tile-pos (make-vec2 (parse-number tilex) (parse-number tiley))
        plant-id (parse-number plant-id)]
    (dosync
     (ref-set my-game-world
              (game-world-modify-tile
               (deref my-game-world)
               tile-pos
               (fn [tile]
                 (tile-modify-entity
                  tile plant-id
                  (fn [plant]
                    (if (< (:version plant) 2)
                      (do
                        (println "updating soil:" soil)
                        (modify :version (fn [v] 2)
                                (merge
                                 {:soil (parse-number soil)} plant)))
                      plant)))))))
    (json {:ok true})))

(defn s-spirit-sprites [name iefix]
  ;(update-islands (str "./" name) (str "./" name))
  (read-islands (str "./public/" name)))

(defn s-perceive [iefix]
  (world-perceive-all (deref fatima-world))
  (json '("ok")))

(defn s-gift [player-id fruit-id receiver-id iefix]
  (let [player-id (parse-number player-id)
        fruit-id (parse-number fruit-id)
        receiver-id (parse-number receiver-id)
        tile (make-vec2 0 0) ; (for the messages) doesn't make sense in this context
        pos (make-vec2 0 0) ; hmmm
        sender (game-world-find-player
                (deref my-game-world)
                player-id)
        receiver (game-world-find-player
                  (deref my-game-world)
                  receiver-id)]
    (dosync
     (let [fruit (player-get-fruit sender fruit-id)]
       (if fruit
         (do
           (game-log sender "player-gift" nil
                     (list (:name receiver) (:type fruit)))
                                        ; this is ridiculous
           (ref-set my-game-world
                    (game-world-modify-player ; modify the sender
                     (game-world-modify-player ; modify the receiver
                      (deref my-game-world) receiver-id
                      (fn [player]
                        (modify
                         :seeds
                         (fn [fruits]
                           (max-cons fruit fruits max-player-fruit))
                         (player-add-msg ; add the recieved message
                          player
                          (make-spirit-msg ; make the message
                           "gift_received" ; this message doesn't get processed
                           (game-world-find-spirit
                            (deref my-game-world)
                            (layer->spirit-name (:layer fruit)))
                           receiver-id tile pos (list (:name receiver)
                                                      (:name sender) (:type fruit)))))))
                     player-id
                     (fn [player]
                       (player-remove-fruit
                        (player-add-msg ; add the sent message
                         player
                         (make-spirit-msg ; make the message
                          "gift_sent" ; this message doesn't get processed
                          (game-world-find-spirit
                           (deref my-game-world)
                           (layer->spirit-name (:layer fruit)))
                          player-id tile pos (list (:name sender)
                                                   (:name receiver) (:type fruit))))
                        fruit-id))))
           (json '("ok")))
         (json '("fail")))))))

(defn s-answer [player-id code index iefix]
  (let [index (parse-number index)]
    (dosync
     (ref-set my-game-world
              (game-world-modify-player
               (deref my-game-world)
               (parse-number player-id)
               (fn [player]
                 (modify
                  :log
                  (fn [log]
                    (log-answer-note log code index))
                  player)))))
    (json '("ok"))))

(defn s-offering [player-id fruit-id spirit iefix]
  (let [player-id (parse-number player-id)
        fruit-id (parse-number fruit-id)
        player (game-world-find-player
                (deref my-game-world)
                player-id)]
    (dosync
     (let [fruit (player-get-fruit player fruit-id)]
       (if fruit
         (do
           (game-log player "spirit-gift" nil
                     (list spirit (:type fruit)))
           (ref-set my-game-world
                    (game-world-modify-player
                     (game-world-modify-spirit
                      (deref my-game-world) spirit
                      (fn [spirit]
                        (modify
                         :offerings
                         (fn [offerings]
                           (max-cons (list player-id fruit) offerings 5))
                         spirit)))
                     player-id
                     (fn [player]
                       (player-remove-fruit player fruit-id))))
           (json '("ok")))
         (json '("fail")))))))

(defn s-get-wormhole-exit [id]
  (json (game-world-get-random-wormhole-coords (deref my-game-world) id)))

(def func-map
     (hash-map
      "login" s-login
      "move-player" s-move-player
      "pull" s-pull
      "grow" s-grow
      "get-msgs" s-get-msgs
      "make-plant" s-make-plant
      "pick" s-pick
      "soil" s-soil
      "spirit-sprites" s-spirit-sprites
      "perceive" s-perceive
      "gift" s-gift
      "answer" s-answer
      "offering" s-offering
      "chat" s-chat
      "get-wormhole-exit" s-get-wormhole-exit))

(defn dispatch [msg]
  (let [parsed (clojure.data.json/read-json msg)
        func (get func-map (:f parsed))
        args (:a parsed)]
    (if func (hash-map
              :f (:f parsed)
              :a (apply func args))
        (hash-map :name "error"))))

(defn start-sockets []
  (println "starting sockets")
  (doto (WebServers/createWebServer 8002)
    (.add "/borrowed-scenery"
          (proxy [WebSocketHandler] []
            (onOpen [c] (println "opened" c))
            (onClose [c] (println "closed" c))
            (onMessage [c j]
                       ;(println "message:" j)
                       (.send c (json (dispatch j))))))
    (.add (StaticFileHandler. "."))
    (.start)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn -main []
  (let [pool (Executors/newFixedThreadPool 2)
        tasks (list
               (fn []
                 (tick))
               (fn []
                 (start-sockets))
               )]
    (doseq [future (.invokeAll pool tasks)]
      (.get future))
    (.shutdown pool)))

