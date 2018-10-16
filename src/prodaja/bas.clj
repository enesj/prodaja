(ns prodaja.bas
  (:use dk.ative.docjure.spreadsheet
        prodaja.data
        prodaja.db
        clojure.java.io)
  (:require
    [clj-time.coerce :as c]
    [clj-time.core :as t]
    [clojure.java.jdbc :as j]
    [clojure.string :as str]

    [com.rpl.specter :as S]
    [honeysql.core :as sql]
    [honeysql.helpers :refer :all :as helpers]))


(def choice (atom :bas))


(def iso #{"ISO"
           "ISO/ASTM"
           "ISO/IEC"
           "ISO/TR"
           "ISO/TS"})

(def iec #{"IEC"
           "IEC Guide"
           "CISPR"
           "IEC/IEEE"
           "IEC/ISO"
           "IEC/TR"
           "IEC/TR3"
           "IEC/TRF"
           "IEC/TS"})

(def all-sources {"BAS  DIN"           :din,
                  "BAS  ETS"           :etsi,
                  "BAS ASTM D"         :astm,
                  "BAS ASTM G"         :astm,
                  "BAS CEN ISO/TR"     :cen,
                  "BAS CEN ISO/TS"     :cen,
                  "BAS CEN/CLC Guide " :cen,
                  "BAS CEN/CLC/TR"     :cen,
                  "BAS CEN/TR"         :cen,
                  "BAS CEN/TS"         :cen,
                  "BAS CISPR"          :iec,
                  "BAS CLC & IEC"      :clc,
                  "BAS CLC/R"          :clc,
                  "BAS CLC/TR"         :clc,
                  "BAS CLC/TS"         :clc,
                  "BAS CR"             :none,
                  "BAS CR ISO"         :iso,
                  "BAS DIN"            :din,
                  "BAS DIN VDE"        :din,
                  "BAS EG"             :none,
                  "BAS EN"             :cen,
                  "BAS EN ISO"         :cen,
                  "BAS EN ISO/IEC"     :cen,
                  "BAS ENV"            :cen,
                  "BAS ENV ISO"        :cen,
                  "BAS ES"             :none,
                  "BAS ETR"            :none,
                  "BAS ETS"            :etsi,
                  "BAS HD"             :hd,
                  "BAS I-ETS"          :etsi,
                  "BAS IEC"            :iec,
                  "BAS IEC TR"         :iec,
                  "BAS IEC TS"         :iec,
                  "BAS IEC/IEEE"       :iec,
                  "BAS IEC/PAS"        :iec,
                  "BAS IEC/TR"         :iec,
                  "BAS IEC/TS"         :iec,
                  "BAS ISO"            :iso,
                  "BAS ISO Guide"      :iso,
                  "BAS ISO/DIS"        :iso,
                  "BAS ISO/IEC"        :iso,
                  "BAS ISO/IEC Guide"  :iso,
                  "BAS ISO/IEC ISP"    :iso,
                  "BAS ISO/IEC TR"     :iso,
                  "BAS ISO/IEC TS"     :iso,
                  "BAS ISO/IEC/IEEE"   :iso,
                  "BAS ISO/IEEE"       :iso,
                  "BAS ISO/OECD"       :iso,
                  "BAS ISO/PAS"        :iso,
                  "BAS ISO/R"          :iso,
                  "BAS ISO/TR"         :iso,
                  "BAS ISO/TS"         :iso,
                  "BAS ISO/WD"         :iso,
                  "BAS IWA"            :iwa,
                  "BAS OHSAS"          :ohsas,
                  "BAS R"              :none,
                  "BAS SN"             :none,
                  "BAS TBR"            :none,
                  "BAS TR"             :none,
                  "BAS TS"             :none,
                  "CEN ISO/IEC TR"     :cen,
                  "EN IEC"             :cen,
                  "ISO/CD"             :iso,
                  "ISO/DIS"            :iso,
                  "ISO/DTS"            :iso,
                  "ISO/IEC FDIS"       :iso,
                  "ISO/NP"             :iso,
                  "ISO/WD"             :iso})



(def jus #{"JUS"})

(def bas #{"BAS"})

(defn std-types [type]
  (cond
    (bas type) 1
    (iso type) 2
    (iec type) 3
    (jus type) 5
    :else 4))

(defn std-types-kw [type]
  (case type
    :bas 1
    :iso 2
    :iec 3
    :jus 5
    :else 4))

(def sluzbe {1 "01"
             2 "02"
             3 "03"
             4 "03/1"
             5 "04"
             6 "04/1"})



(def select-ponude
  "SELECT Ponude.ID, Ponude.Status, Ponude.Broj, Ponude.PrimaOrganizacija, Ponude.PrimaAdresa, Ponude.Popust,
  Ponude.DatumPrijema, Ponude.Storno, Ponude.Euro, Ponude.Sluzba, Ponude.DatumSlanja FROM Ponude
  WHERE Ponude.Status = 3 AND Ponude.Storno = 0;")


(def bas-standards-sql
  "SELECT
    national_standard_id as standard_id,
    standard_code
  FROM ts.national_standard")

(def bas-standards-products-sql
  "SELECT ts.national_standard.standard_code as standard_code, ts.natstandard_document.natstandard_document_id as p_id,
  ts.natstandard_document.national_standard_id as standard_id, ts.type_natstd.name as source
  FROM ts.national_standard INNER JOIN
  ts.natstandard_document ON ts.national_standard.national_standard_id = ts.natstandard_document.national_standard_id
  INNER JOIN ts.type_natstd ON ts.national_standard.type_natstd_id = ts.type_natstd.type_natstd_id
  WHERE ts.natstandard_document.is_product = 1")

(def iso-standards-sql
  "SELECT pr10_001 as standard_id, pr20_104 as standard_code FROM ts.iso_product")

(def iec-standards-sql "SELECT ID as standard_id, REFERENCE as standard_code FROM ts.iec_pub ")

(def jus-standards-sql "SELECT id as p_id, JUSId as standard_id FROM ts.jus")

(def ponude-stavke-sql
  "SELECT
  PonudaStavkeSve.id as invoice_product_id,
  PonudaStavkeSve.stavka as standard_code_old,
  PonudaStavkeSve.standardid as old_id,
  PonudaStavkeSve.ponudaid as invoice_id,
  PonudaStavkeSve.kolicina as p_quantity,
  PonudaStavkeSve.cijena as p_price,
  PonudaStavkeSve.CijenaDev as currency_amount,
  Ponude.DatumSlanja,
  Ponude.euro as currency_id,
  PonudaStavkeSve.popust as p_discount
  FROM PonudaStavkeSve INNER JOIN Ponude ON PonudaStavkeSve.PonudaID = Ponude.ID
  WHERE Ponude.status = 3 AND Ponude.Storno = 0")

(def all-bas-sales-sql
  "SELECT pay.invoice.date_order, pay.invoice.currency_id, pay.invoice.is_valid, pay.invoice_product.p_name, pay.invoice_product.p_quantity, pay.invoice.invoice_number,
  pay.invoice_product.p_price, pay.invoice_product.p_discount, pay.invoice_product.p_id, ts.type_natstd.name as source, ts.natstandard_document.national_standard_id as standard_id,
  ts.language.name as lang, pay.invoice_product.currency_amount
  FROM ts.national_standard
  INNER JOIN ts.type_natstd ON ts.national_standard.type_natstd_id = ts.type_natstd.type_natstd_id
  INNER JOIN ts.natstandard_document
  INNER JOIN ts.language ON ts.natstandard_document.language_id = ts.language.language_id
  ON ts.national_standard.national_standard_id = ts.natstandard_document.national_standard_id
  INNER JOIN pay.invoice
  INNER JOIN pay.invoice_product ON pay.invoice.invoice_id = pay.invoice_product.invoice_id
  ON  ts.natstandard_document.natstandard_document_id = pay.invoice_product.p_id
  WHERE (pay.invoice.is_valid = 1)")

(def all-iec-sales-sql
  "SELECT pay.invoice_product.p_type, pay.invoice.date_order, pay.invoice.currency_id, pay.invoice.is_valid, pay.invoice_product.p_quantity, pay.invoice.invoice_number,
   pay.invoice_product.p_price, pay.invoice_product.p_discount, pay.invoice_product.p_name, pay.invoice_product.p_id, ts.iec_pub.ID as standard_id,
   ts.iec_pub.LANGUAGE as lang, pay.invoice_product.currency_amount
   FROM pay.invoice_product
   INNER JOIN
   pay.invoice ON pay.invoice_product.invoice_id = pay.invoice.invoice_id
   INNER JOIN
   ts.iec_pub ON pay.invoice_product.p_id = ts.iec_pub.ID
   WHERE (pay.invoice.is_valid = 1) AND (pay.invoice_product.p_type = 3)")

(def all-iso-sales-sql
  "SELECT pay.invoice_product.p_type, pay.invoice.date_order, pay.invoice.currency_id, pay.invoice.is_valid, pay.invoice_product.p_quantity, pay.invoice.invoice_number,
  pay.invoice_product.p_price, pay.invoice_product.p_discount, pay.invoice_product.p_name, pay.invoice_product.p_id, ts.iso_product.pr10_001 as standard_id,
  ts.iso_product.pr10_299langref as lang, pay.invoice_product.currency_amount
  FROM pay.invoice_product
  INNER JOIN
  pay.invoice ON pay.invoice_product.invoice_id = pay.invoice.invoice_id
  INNER JOIN
  ts.iso_product ON pay.invoice_product.p_id = ts.iso_product.pr10_001
  WHERE (pay.invoice.is_valid = 1) AND (pay.invoice_product.p_type = 2)")

(def all-other-sales
  "SELECT pay.invoice_product.p_type, pay.invoice.date_order, pay.invoice.currency_id, pay.invoice.is_valid, pay.invoice_product.p_quantity, pay.invoice.invoice_number,
   pay.invoice_product.p_price, pay.invoice_product.p_discount, pay.invoice_product.p_name, pay.invoice_product.currency_amount
   FROM pay.invoice_product
   INNER JOIN
   pay.invoice ON pay.invoice_product.invoice_id = pay.invoice.invoice_id
   WHERE (pay.invoice.is_valid = 1) AND (pay.invoice_product.p_type = 4)")


(def standard-type {:bas {:sql bas-standards-sql :template bas}
                    :iec {:sql iec-standards-sql :template iec}
                    :iso {:sql iso-standards-sql :template iso}
                    :jus {:sql jus-standards-sql :template jus}})


(defn std-type []
  (standard-type @choice))



(defn bas-stadards-products []
  (get_data smis-conn bas-standards-products-sql))

(def ponude-stavke
  (get_data standardoteka-conn ponude-stavke-sql))

(defn modify-stavka [std-code]
  (let [[stavka lang] (str/split (str/trim (:standard_code_old std-code)) #"\(")
        lang (if lang
               (str/trim (first (str/split lang #"\)")))
               "")
        stavka (-> (str/trim stavka)
                   (str/replace #"(?i)BAS Katalog" "Katalog")
                   (str/replace #"  " " "))
        type (first (str/split stavka #" "))]
    (zipmap [:standard_code_old :lang :type] [stavka lang type])))


(defn modify-id [id]
  (let [std-id (read-string id)]
    (if (and std-id (< std-id 63000)) std-id nil)))

(defn code-corrections [code-key std-codes]
  (->> std-codes
       (S/transform [S/ALL code-key] #(->
                                        (str/replace % #"Cor " "Cor")
                                        (str/replace #"Amd " "A")
                                        (str/replace #"Cor:" "Cor1:")
                                        (str/replace #" S1:" ":")
                                        (str/replace #": 2" ":2")
                                        (str/replace #"Vodič " "Guide ")))))
(defn zero-price [std]
  (and (= (:p_price std) 0.0)
       (not (or (.contains (:standard_code_old std) "/AC")
              (.contains (:standard_code_old std) "Cor")))))

(defn prepare-stavke []
  (->> ponude-stavke
       (remove #(or (empty? (:standard_code_old %)) (zero-price %)))
       (pmap #(merge % (modify-stavka %)))))

(defn other-products []
  "(take 500) - limit name to 500 chars"
  (->> (prepare-stavke)
       (filter #((complement (set (concat bas iso iec jus))) (:type %)))
       (S/transform [S/ALL :currency_id] #(if % 2 1))
       (S/transform [S/ALL :p_discount] #(if % % 0.0))
       (pmap #(merge % {:p_type 4}))
       (pmap #(clojure.set/rename-keys % {:standard_code_old :p_name,}))
       (S/transform [S/ALL :p_name] #(when % (->> (str/replace % #"\s+" " ")
                                                  (take 500)
                                                  (apply str))))))

(defn all-ids []
  "{standard-code standard-id
    .....}"
  (let [type ((std-type) :template)
        p_type (std-types-kw @choice)]
    (->> (prepare-stavke)
         (filter #(type (:type %)))
         (pmap #(merge % {:p_type p_type}))
         (S/transform [S/ALL :old_id] #(if (and % (= type bas))
                                        (modify-id %)
                                        (when (= type jus) %)))
         (S/transform [S/ALL :currency_id] #(if % 2 1))
         (S/transform [S/ALL :p_discount] #(if % % 0.0))
         (code-corrections :standard_code_old))))

(defn smis-standards []
  (->>
     (get_data smis-conn (eval ((std-type) :sql)))
     (code-corrections :standard_code)))


(defn smis-standardoteka-by-standard-code []
  (clojure.set/join (all-ids) (smis-standards) {:standard_code_old :standard_code}))


(defn smis-standardoteka-by-standard-id []
  (->>
    (clojure.set/join (smis-standards) (all-ids) {:standard_id :old_id})
    (filter #(not= (:standard_code_old %) (:standard_code %)))))

(defn bas-all []
  (->>
    (clojure.set/union (smis-standardoteka-by-standard-code) (smis-standardoteka-by-standard-id))))


(defn bas-products-ids []
  (->>
    (clojure.set/join (bas-stadards-products) (bas-all) {:standard_id :standard_id})
    (group-by :invoice_product_id)
    (pmap (comp first second))))


(def lang-variants
  {"BAS EN ISO 9001:2017" ["bs,en" "hr,en"]
   "BAS EN ISO 9000:2017" ["bs,en" "hr,en"]
   "BAS EN ISO 707:2010"  ["bs" "en"]})



(defn ok-ids []
  (->>
    (smis-standardoteka-by-standard-code)
    (pmap #(clojure.set/rename-keys %
                                    {:standard_id :p_id}))))
;:id :invoice_product_id}))))

(defn all-bas []
  (reset! choice :bas)
  (bas-products-ids))

(defn all-iec []
  (reset! choice :iec)
  (->>
    (ok-ids)
    (group-by :invoice_product_id)
    (pmap (comp first second))))

(defn all-iso []
  (reset! choice :iso)
  (ok-ids))

(defn all-jus []
  (reset! choice :jus)
  (->>
     (clojure.set/join (get_data smis-conn (eval ((std-type) :sql))) (all-ids) {:standard_id :old_id})))
;(pmap #(clojure.set/rename-keys % {:id :invoice_product_id}))))

(defn all-stds []
  (->>
    (concat (all-bas) (all-iec) (all-iso) (all-jus) (other-products))
    (pmap #(clojure.set/rename-keys % {:standard_code :p_name,}))
    (pmap #(update-in % [:p_name] (fn [x] (str x " (" (:lang %) ")"))))
    (S/transform [S/ALL :p_name] #(when % (->> (str/replace % #"\s+" " ")
                                               (take 500)
                                               (apply str))))
    (pmap #(select-keys %
                        [:invoice_id, :p_id, :p_name, :p_type :currency_amount
                         :p_quantity, :p_price :currency_id :p_discount]))))


(defn invoice-values []
  ;(sort-by #(nth % 7)
  (mapv
    (fn [x]
      (let [{:keys [:id, :status :broj, :primaorganizacija, :primaadresa, :popust, :datumprijema,
                    :storno, :euro, :datumprijema, :sluzba :dds_base :datumslanja]} x]
        (->>
          [id, 2, broj, 76, primaorganizacija, primaadresa, popust, (if datumprijema datumprijema datumslanja), (if storno 0 1),
           (if euro 2 1), (if datumprijema datumprijema datumslanja), (sluzbe sluzba), 0]
          (S/transform (S/nthpath 4) #(when % (->> (str/replace % #"\s+" " ")
                                                   (take 100)
                                                   (apply str)))))))
    (get_data standardoteka-conn select-ponude)))



(defn insert-invoices []
  (let [value-groups (partition-all 50 (invoice-values))]
    (doseq [values-group value-groups]
      (->>
        (-> (insert-into :pay.invoice)
            (columns :invoice_id, :type_invoice_id, :invoice_number, :user_profile_id, :c_name, :c_address,
                     :discount, :date_order, :is_valid, :currency_id, :date_payment :invoice_number_prefix :dds_base)
            (values values-group)
            sql/format)
        (S/transform S/FIRST #(str "SET IDENTITY_INSERT pay.invoice ON; " %))
        (j/execute! smis-conn)))))


(defn insert-invoice-products []
  (let [value-groups (partition-all 50 (all-stds))]
    (doseq [values-group value-groups]
      (->>
        (-> (insert-into :pay.invoice_product)
            (values values-group)
            sql/format)
        (j/execute! smis-conn)))))


;--------------------------------------------------------------------------------------------
;REPORTS
;----------------------------------------------

(def date-range ["2018-06-30" "2018-09-30"])


(defn sources [type]
  "if type = nill lists all sources"
  (->>
    (if type (filter #(= (val %) type) all-sources) all-sources)
    (map first)
    (into #{})))

(defn filter-dates [date]
  (t/within?
    (t/interval
      (c/from-string (first date-range))
      (c/from-string (second date-range)))
    (c/to-date-time date)))


(defn agregate-product-data [product-data]
  (reduce
    (fn [x y]
      (vector
        (:standard_id y)
        (conj (x 1) (:p_name y))
        (conj (x 2) (:lang y))
        (conj (x 3) (:invoice_number y))
        (conj (x 4) (.format (java.text.SimpleDateFormat. "dd/MM/yyyy") (:date_order y)))
        (conj (x 5) (double (if (:p_price y) (:p_price y) 0)))
        (conj (x 6) (double (if (:currency_amount y) (:currency_amount y) 0)))
        (int (+ (x 7) (:p_quantity y)))
        (double (+ (x 8) (* (:p_quantity y)
                            (:p_price y)
                            (- 1 (/ (:p_discount y) 100)))))))
    [[] [] [] [] [] [] [] 0 0]
    product-data))


(defn group-sales-data [data]
  (->>
    (group-by #(:p_id %) data)
    (map #(agregate-product-data (second %)))
    (sort-by #(last (butlast %)) >)))

(defn bas-report [type]
  ":iec, :iso nill -> all stds"
  (->>
    (get_data smis-conn all-bas-sales-sql)
    (filter #((sources type) (:source %)))
    (filter #(filter-dates (:date_order %)))
    group-sales-data))

(defn iec-report []
  (->>
    (get_data smis-conn all-iec-sales-sql)
    (filter #(filter-dates (:date_order %)))
    group-sales-data))

(defn iso-report []
  (->>
    (get_data smis-conn all-iso-sales-sql)
    (filter #(filter-dates (:date_order %)))
    group-sales-data))

(def other-types
  {:bs  #{"BS" "CWA"}
   :din #{"DIN" "AD"}})


(defn other-report [type]
  ":bs, :din"
  (->>
    (get_data smis-conn all-other-sales)
    (filter #(:p_name %))
    (pmap #(merge % {:source (first (str/split (:p_name %) #" "))
                     :p_id   (:p_name %)}))
    (filter #((type other-types) (:source %)))
    (filter #(filter-dates (:date_order %)))
    group-sales-data))

(defn prepare-report [report]
  ;(println (mapv type (first report)))
  (->> report
       (S/transform [S/ALL S/ALL] str)
       (mapv #(into [] (rest %)))))

(defn prepare-excel [reports]
  (let [wb (create-workbook " " nil)]
    (doseq [report reports]
      (let [[name data] report
            sheet (add-sheet! wb name)]
        (add-rows! sheet
                   (into [["Oznaka" "Jezik" "Broj racuna" "Datum" "Cijena KM" "Cijan Dev" "Kolicina" "Iznos"]]
                         (prepare-report data)))))
    (save-workbook! "Izvjestaji o prodaji.xlsx" wb)))




(defn ino-repors []
  (let [reports [["BAS-IEC" (bas-report :iec)]
                 ["IEC" (iec-report)]
                 ["BAS-ISO" (bas-report :iso)]
                 ["ISO" (iso-report)]
                 ["BS" (other-report :bs)]
                 ["DIN" (other-report :din)]]]
    (prepare-excel reports)))


(comment

  (insert-invoices)

  (insert-invoice-products)

  ;reports
  ;--------------------------------------------------------------------------------------------

  (ino-repors)

  ;--------------------------------------------------------------------------------------------

  (prepare-report (iec-report))

  (prepare-report (bas-report :iec))

  (prepare-report (iso-report))

  (prepare-report (bas-report :iso))

  (prepare-report (other-report :bs))

  (prepare-report (other-report :din)))
