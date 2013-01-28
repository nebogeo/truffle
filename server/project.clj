(defproject oak "1.0.0"
  :description "a game server"
  :main oak.core
  :dependencies [[org.clojure/clojure "1.4.0"]
                 [org.clojure/math.numeric-tower "0.0.1"]
                 [org.clojure/algo.generic "0.1.0"]
                 [org.clojure/data.json "0.1.2"]
                 [congomongo "0.1.9"]
                 [org.mongodb/mongo-java-driver "2.7.3"]
                 [org.webbitserver/webbit "0.4.3"]
                 [clj-http "0.1.3"]
                 [mysql/mysql-connector-java "5.1.6"]
                 [org.clojure/java.jdbc "0.0.4"]
                 [korma "0.3.0-beta7"]])
