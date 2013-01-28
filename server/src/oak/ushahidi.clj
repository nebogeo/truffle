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

(ns oak.ushahidi
  (:require [clj-http.client :as client])
  (:use
   ;clojure.math.numeric-tower
   clojure.algo.generic.math-functions
   [clojure.data.json :only (read-json json-str)]
   oak.vec2
   clojure.java.jdbc))

;; sellotape and scissors in here, mixing normal requests via the public
;; api and direct database manipulation

(def url "http://borrowed-scenery.org/zizim/api")
(def centre (list 51.04751 3.72739))
;;(def centre (list 51.04672 3.73121))
(def zoom 18)
(def map-tile-div 5) ; num of splits in map.js
(def game-tile-div 5) ; num of divisions in game tile world

;; latlon to osm map index
(defn latlon-to-tile [lat,lon,zoom] 
  (let [m (pow 2 zoom)
        lat_rad (* lat (/ (Math/PI) 180))]
    (list
     (* (/ (+ lon 180) 360) m)
     (* (/ (- 1 (/ (log (+ (tan lat_rad) (/ 1 (cos lat_rad))))
                   (Math/PI))) 2) m))))

(defn floor-arr [arr]
  [(int (floor (nth arr 0))) (int (floor (nth arr 1)))])

(def origin-tile
     (floor-arr
      (latlon-to-tile (nth centre 0) (nth centre 1) zoom)))

(defn ushahidi-incident-to-tile [incident]
  (latlon-to-tile
   (Float/parseFloat (:locationlatitude (:incident incident)))
   (Float/parseFloat (:locationlongitude (:incident incident)))
   zoom))
 
;; ushahidi/boskoi map comprises
;; tiles split into 4X4 subtiles (map-tile-div)
;; [][][][] [][][][] 
;; [][][][] [][][][]
;; [][][][] [][][][]
;; [][][][] [][][][]

;; game world map comprises:
;; tiles split into 5X5 positions (game-tile-div)
;; [][][][][] [][][][][] 
;; [][][][][] [][][][][]
;; [][][][][] [][][][][]
;; [][][][][] [][][][][]
;; [][][][][] [][][][][]

;; easiest way to convert is to calculate world position and then
;; tile/pos from that (and sub position with fraction)
(defn incident->game-position-fract [incident]
  (let [tile (ushahidi-incident-to-tile incident)
        x (* (- (nth origin-tile 0) (nth tile 0)) map-tile-div)
        y (* (- (nth origin-tile 1) (nth tile 1)) map-tile-div)]
    {:x (int (floor x)) :y (int (floor y))
     :xf (mod x 1)
     :yf (mod y 1)}))

(defn game-pos->tile-pos [pos]
  {:tile-pos (make-vec2 (int (floor (/ (:x pos) game-tile-div)))
                        (int (floor (/ (:y pos) game-tile-div))))
   :pos (make-vec2 (mod (:x pos) game-tile-div)
                   (mod (:y pos) game-tile-div))
   :fract (make-vec2 (:xf pos) (:yf pos))})

;;(defn ushahidi-incident->pos [incident]
;;  (game-pos->tile-pos
;;   (incident->game-position-fract
;;     incident)))

(defn ushahidi-incident->pos [incident]
  (let [tile (ushahidi-incident-to-tile incident)
        wx (- (nth tile 0) (nth origin-tile 0))
        wy (- (nth tile 1) (nth origin-tile 1))]
    {:tile-pos (make-vec2 (int (floor wx)) (int (floor wy)))
     :pos (make-vec2 (int (floor (mod (* wx game-tile-div) game-tile-div)))
                     (int (floor (mod (* wy game-tile-div) game-tile-div))))
     :fract (make-vec2 (mod (* wx game-tile-div) 1)
                       (mod (* wy game-tile-div) 1))}))

;;-----------------------------------------------------------------
;; input - via the ushahidi api

; todo - error check!
(defn ushahidi-get-incidents-since [id]
  (:incidents
   (:payload
    (read-json
     (:body
      (client/get (str url "?task=incidents&by=sinceid&id=" id "&limit=5000")))))))

(defn ushahidi-get-categories [id]
  (read-json (:body (client/get (str url "?task=category&id=" id)))))

;;(println (latlon-to-tile (nth centre 0) (nth centre 1) zoom))

;;-----------------------------------------------------------------
;; output - direct via sql! :(

(defn read-secret []
  (let [s (slurp "secret.txt")]
    (subs s 0 (- (count s) 1))))

(def db {:classname "com.mysql.jdbc.Driver"
         :subprotocol "mysql"
         :subname "//localhost:3306/borrowed_scenery"
         :user "root"
         :password (read-secret)})

(defn current-sql-time []
  (.format (new java.text.SimpleDateFormat "yyyy-MM-dd hh:mm:ss")
           (new java.util.Date)))

(defn insert-comment [id name text time]
  (insert-values :comment
                 [:incident_id
                  :comment_author
                  :comment_description
                  :comment_email
                  :comment_date
                  :comment_active]
                 [id name text "nebogeo@gmail.com"
                  time 1]))

(defn ushahidi-add-incident-comment [id name text]
  (let [time (current-sql-time)]
  (with-connection db
    (insert-comment id name text time))))




