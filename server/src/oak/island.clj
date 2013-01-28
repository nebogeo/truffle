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

(ns oak.island
  (:require clojure.java.io))

(defn execute [command]
  (println (str "executing: " command))
  (let [process (.exec (Runtime/getRuntime) command)]
    (if (= 0 (.waitFor  process))
        (.getInputStream process) ; read-lines removed
        (.getErrorStream process))))

(defn update-islands [srcpath dstpath]
  (comment when (.exists (file srcpath))
    (execute (str "rm -r " dstpath "islands"))
    (execute (str "cp -r " srcpath " " dstpath))
    (execute (str "rm -r " srcpath))
    ))

(defn read-islands [path]
  (comment json/encode-to-str
   (map
    (fn [file]
      (let [toks (.split (str file) "-")]
        {:name (.substring (str file) 8)
         :position
         {:x (nth toks 2)
          :y (first (.split (nth toks 3) "\\."))}}))
    (filter #(re-find #"\.png$" (str %)) (file-seq (java.io.File. path))))))

