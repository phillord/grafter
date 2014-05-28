(ns grafter.tabular
  (:require [clojure.java.io :as io]
            [grafter.tabular.common :as tabcommon]
            [grafter.tabular.excel]
            [grafter.tabular.csv]
            [incanter.core :as i])
  (:import [incanter.core Dataset]))


(def open-tabular-file
  "Takes a File or String as an argument and coerces it based upon its
  file extension into a concrete grafter table.

Supported files are currently csv or Excel's xls or xlsx files.

Additionally open-as-table takes an optional set of key/value
parameters which will be passed to the concrete function opening the
file.

Supported options are currently:

:ext - An overriding file extension (as keyword) to force a particular
       file type to be opened instead of looking at the files extension.
"

  tabcommon/open-tabular-file)

(def open-all-sheets
  tabcommon/open-all-sheets)

(defn nnth
  "Same as nth but returns nil (or not-found) if supplied."
  ([col index] (nnth col index nil))
  ([col index not-found]
     (try
       (nth col index not-found)
       (catch java.lang.IndexOutOfBoundsException ex
         not-found))))

(defn- select-columns-from-row [cols row]
  ;; Makes use of the fact that rows (vectors) are functions
  ;; of their indices.
  (apply vector (map (apply vector row) cols)))

(defn columns [csv & cols]
  "Takes a parsed CSV file and any number of integers corresponding to
column numbers and returns a new CSV file containing only those
columns."
  (map (partial select-columns-from-row cols) csv))

(defn rows
  ([csv] csv)
  ([csv r]
     (if (sequential? r)
       (apply rows csv r)
       (let [val (nnth csv r ::not-found)]
         (if (= ::not-found val)
           nil
           (list val)))))
  ([csv r & rs]
     ;; todo make this lazy
     (reduce (fn [acc r]
               (conj acc (nnth csv r)))
             [] (conj rs r))))

(defn drop-rows [csv n]
  "Drops the first n rows from the CSV."
  (drop n csv))

(defn take-rows [csv n]
  "Drops the first n rows from the CSV."
  (take n csv))

(defmulti grep
  "Filters rows in the table for matches.  This is multi-method
  dispatches on the type of its second argument.  It also takes any
  number of column numbers as the final set of arguments.  These
  narrow the scope of the grep to only those columns.  If no columns
  are specified then grep operates on all columns."
  (fn [table f & cols] (class f)))

(defmethod grep clojure.lang.IFn [csv f & cols]
  (let [select-cols (if (empty? cols)
                           identity
                           (partial select-columns-from-row cols))]
    (filter (fn [row]
              (some f (select-cols row))) csv)))

(defmethod grep java.lang.String [csv s & cols]
  (apply grep csv #(.contains % s) cols))

(defmethod grep java.util.regex.Pattern [csv p & cols]
  (apply grep csv #(re-find p %) cols))

(defn- remove-indices [col & idxs]
  "Removes the values at the supplied indexes from the given vector."
  (let [pos (map - (sort idxs) (iterate inc 0))
        remove-index (fn [col pos]
                       (vec (concat (subvec col 0 pos)
                                    (subvec col (inc pos)))))]
    (reduce remove-index col pos)))

(defn- fuse-row [columns f row]
  (let [to-drop (drop 1 (sort columns))
        merged (assoc row (apply min columns)
                      (apply f (select-columns-from-row columns row)))]
    (apply remove-indices merged to-drop)))

(defn fuse [csv f & cols]
  "Merge columns with the specified function f receives the number of
cols supplied number of arguments e.g. If you fuse 3 columns f
must accept 3 arguments"
  (map (partial fuse-row cols f) csv))

(defn mapr [csv f]
  "Logically the same as map but with reversed arguments, so it works
better with -> .  mapr maps f over each row."
  (map f csv))

(defn mapc [csv fs]
  "Takes an array of functions and maps each to the equivalent column
position for every row."
  (->> csv
       (map (fn [row]
              (map (fn [f c] (f c))
                   (lazy-cat fs (cycle [identity])) row)))
       (map (partial apply vector))))

(defn swap [csv col-map]
  "Takes a map from column_id -> column_id (int -> int) and swaps each
column."

  (map (fn [row]
         (reduce (fn swaper [acc [cola colb]]
                   (-> row
                       (assoc cola (row colb))
                       (assoc colb (row cola))))
                 [] col-map))
       csv))

(defn derive-column
  "Adds a new column to the end of the row which is derived from
column with position col-n.  f should just return the cells value.

If no f is supplied the identity function is used, which results in
the specified column being cloned."

  ;; todo support multiple columns/arguments to f.
  ([csv col-n]
     (derive-column csv identity col-n))

  ([csv f col-n]
     (map #(conj % (f (nth % col-n))) csv)))

;; alias to create a lightweight pattern matching style syntax for use
;; with mapc
(def _ identity)

(defn select-columns
  ([srange row]
     (drop srange row))
  ([srange erange row]
     {:pre [(<= srange erange)]}
     (let [ncols (- (inc erange) srange)]
       (->> row
            (drop srange)
            (take ncols)))))


;; TODO fix this so that it doesn't assume contiguous blocks of
;; id/measure columns.  It needs to calculate the set complement of
;; the selected measure column ids and use those.

(defn normalise [[header-row & data-rows] measure-col-ids]
  "Takes a CSV with a header row and normalises it by transforming the
selected columns into values within the rows.

Essentially the following call:

(normalise csv 3 4)

will convert a table that looks like this:

| cola | colb | colc | normalise-me-a | normalise-me-b |
|------+------+------+----------------+----------------|
|    0 |    0 |    0 | normal-a-0     | normal-b-0     |
|    1 |    1 |    1 | normal-a-1     | normal-b-1     |

into data rows look like this.  It does not yet preserve the header row:

|   |   |   |                |            |
|---+---+---+----------------+------------+
| 0 | 0 | 0 | normalise-me-a | normal-0-a |
| 0 | 0 | 0 | normalise-me-b | normal-0-b |
| 1 | 1 | 1 | normalise-me-a | normal-1-a |
| 1 | 1 | 1 | normalise-me-b | normal-1-b |
"
  (let [ncols            (count header-row)
        colids           (-> (take ncols measure-col-ids) set sort) ;; incase its an infinite seq
        srange           (first colids)
        colids           (take (- ncols srange) colids)
        erange           (last colids)
        ncols            (count colids)
        headers-to-move  (select-columns srange erange header-row)

        normalise-row (fn [id row]
                        (let [rowv (->> row (take srange) (apply vector))]
                          (-> rowv
                              (conj (nth header-row id))
                              (conj (nth row id)))))

        expand-rows (fn [row] (map normalise-row colids (repeat ncols row)))]

    (mapcat expand-rows data-rows)))

(comment
  ;; TODO implement inner join, maybe l/r outer joins too
  (defn join [csv f & others]
    ;;(filter)
    (apply map vector csv others)))