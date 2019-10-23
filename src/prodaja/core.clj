(ns prodaja.core
  (:use prodaja.data prodaja.db clojure.java.io)
  (:require
            [com.rpl.specter :as S]
            [clojure.string :as str]))

(def smis-standards-sql
  "SELECT
    national_standard_id as standard_id,
    standard_code
  FROM ts.national_standard")

(def smis-standards
  (get_data smis-conn smis-standards-sql))

(def ponude-sql
  "SELECT * FROM dbo.Ponude")

(def ponude-stavke-sql
  "SELECT * FROM PonudaStavkeSve INNER JOIN Ponude ON PonudaStavkeSve.PonudaID = Ponude.ID")

(def ponude
  (get_data standardoteka-conn ponude-sql))

(def ponude-stavke
  (get_data standardoteka-conn ponude-stavke-sql))

(defn all-ids []
      (->> ponude-stavke
           (S/transform [S/ALL] (juxt :stavka :standardid :id :vrsta :status))
           (remove (comp nil? first))
           (remove #(= "" (first %)))
           (remove #(not= 3 (last %)))
           (S/transform [S/ALL S/FIRST] str/trim)
           distinct
           (S/transform S/ALL #(take 3 %))
           (filter #(= "BAS" (apply str (take 3 (first %)))))
           (group-by first)
           (sort-by first)
           (S/transform [S/ALL S/LAST] #(sort-by second %))
           (map #(vector (count (second %)) (second %)))
           (remove #(= (first %) 1))))



(defn all-ids-map []
  (->> ponude-stavke
       (S/transform [S/ALL] (juxt :stavka :standardid :id :vrsta :status))
       (remove (comp nil? first))
       (remove #(= "" (first %)))
       (remove #(not= 3 (last %)))
       (S/transform [S/ALL S/FIRST] str/trim)
       distinct
       (S/transform S/ALL #(take 3 %))
       (filter #(= "BAS" (apply str (take 3 (first %)))))
       (map vec)
       (mapv #(vector (first %) (vector(second %) (last %))))
       (into {})
       (S/transform [S/MAP-VALS S/FIRST]
                    #(if (and (string? %)(> (read-string %) 65534)) nil %))))

(defn nil-to-id []
  (->> (all-ids)
    (remove #(not= (first %) 2))
    (mapv second)
    (filter #(nil? (second (first %))))
    (mapv second)
    (map vec)
    (into {})))

(defn two-ids []
  (->> (all-ids)
       (remove #(not= (first %) 2))
       (mapv second)
       (remove #(nil? (second (first %))))))

(defn more-ids []
  (->> (all-ids)
       (remove #(= (first %) 2))
       (mapv second)))

(def update-ids
  {"BAS EN 3-7+A1:2009 (en)" "18677"
   "BAS EN 590:2008 (bs)" "17337"
   "BAS EN ISO 5667-1:2008 (en)" "15098"
   "BAS EN 60947-3:2010 (en)" "53973"
   "BAS EN 61000-3-2:2008 (en)" "14563"
   "BAS EN 61000-4-2:2010 (en)" "54687"
   "BAS EN 62271-1:2010 (en)" "53955"
   "BAS EN ISO 10211:2008 (en)" "17515"
   "BAS EN ISO 13366-2:2008 (en)" "16452"
   "BAS EN ISO 14121-1:2009 (en)" "18141"
   "BAS EN ISO 6579/AC:2010 (en)" "53644"
   "BAS EN ISO 7396-1:2008 (en)" "14635"
   "BAS EN ISO/IEC 17021:2007 (en)" "13381"})


(defn updated-ids []
  (merge (all-ids-map) update-ids (nil-to-id)))

(defn not-found-ids []
 (->>
  (filter #(nil? (second %)) (updated-ids))
  ;(map first)
  (S/transform [S/ALL S/FIRST] #(str/split % #"\("))
  (S/transform [S/ALL S/FIRST] str/trim)))
  ;(mapv str/trim)))

(defn smis-id [code]
  (->>
    (filter #(= (:standard_code %)   code) smis-standards)
    first
    :standard_id))

(defn main
  [] nil)
