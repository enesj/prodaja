(ns prodaja.db
  (:use prodaja.data)
  (:require [clojure.java.jdbc :as j]
            [clojure.string :as str]))



(def smis-conn {:classname  "com.microsoft.jdbc.sqlserver.SQLServerDriver"
                :subprotocol "sqlserver"
                :subname     (str smis-host ";database=" smis-db ";user=" smis-un ";password=" smis-pw)})


(def standardoteka-conn {:classname  "com.microsoft.jdbc.sqlserver.SQLServerDriver"
                         :subprotocol "sqlserver"
                         :subname     (str standardoteka-host ";database=" standardoteka-db
                                           ";integratedSecurity=true")})

(defn get_data [conn sql]
  (j/query conn [sql]))

