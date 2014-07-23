(ns grafter.tabular-test
  (:require [clojure.test :refer :all]
            [grafter.tabular :refer :all]
            [grafter.tabular.csv]
            [grafter.tabular.excel]
            [grafter.sequences :as seqs]
            [incanter.core :as inc]
            [me.raynes.fs :as fs]))

(deftest header-functions-tests
  (let [raw-data [[:a :b :c] [1 2 3] [4 5 6]]]

    (testing "copy-first-row-to-header"
      (let [retval (copy-first-row-to-header raw-data)]
        (testing "returns a pair"
          (testing "where first item is the header"
            (is (= [:a :b :c] (first retval))))
          (testing "and the second item is the unmodified source data"
            (is (= raw-data (second retval)))))))

    (testing "move-first-row-to-header"
      (let [retval (move-first-row-to-header raw-data)]
        (testing "returns a pair"
          (testing "where first item is the header"
            (is (= [:a :b :c] (first retval))))
          (testing "and the second item is the source data without the first row"
            (is (= (rest raw-data) (second retval)))))))))

(deftest make-dataset-tests
  (testing "make-dataset"
    (let [raw-data [[1 2 3] [4 5 6]]]

      (testing "converts a seq of seqs into a dataset"
        (is (instance? incanter.core.Dataset
                       (make-dataset raw-data))))

      (testing "assigns column names alphabetically by default"
        (let [header (:column-names (make-dataset raw-data))]
          (is (= ["A" "B" "C"] header))))

      (testing "takes a function that extracts the column names (header row)"
        (let [dataset (make-dataset move-first-row-to-header raw-data)
              header (:column-names dataset)]

          (is (= [1 2 3] header)))))))

;;; These two vars define what the content of the files
;;; test/grafter/test.csv and test/grafter/test.xlsx should look like
;;; when loaded.
;;;
;;; - CSV data is always cast as Strings
;;; - Excel data when loaded is cast to floats

(def raw-csv-data [["one" "two" "three"]
                   ["1" "2" "3"]
                   ["4" "5" "6"]])

(def raw-excel-data [["one" "two" "three"]
                     [1.0 2.0 3.0]])

(def csv-sheet (make-dataset move-first-row-to-header raw-csv-data))

(def excel-sheet (make-dataset move-first-row-to-header raw-excel-data))

(comment
  (testing "returns a lazy-seq of all datasets beneath a path"
    (open-all-sheets)
    ))

(deftest open-tabular-file-tests
  (testing "open-tabular-file"
    (testing "Opens CSV files"
      (let [loaded-csv (open-tabular-file "./test/grafter/test.csv")]
        (testing "are a seq of seqs"
          (is (= raw-csv-data loaded-csv)))))

    (testing "Opens Excel files"
      (let [loaded-excel (open-tabular-file "./test/grafter/test.xlsx")]
        (testing "are incanter.core.Dataset records"
          (is (instance? org.apache.poi.xssf.usermodel.XSSFWorkbook loaded-excel)))))))

(deftest open-all-datasets-tests
  (testing "open-all-datasets"
    (let [sheets (open-all-datasets "./test/grafter" :make-dataset-fn (partial make-dataset move-first-row-to-header))]

      (is (seq? sheets) "should yield a seq")
      (is (= 2 (count sheets)) "should be 2 datasets")

      (let [[loaded-csv-sheet loaded-excel-sheet] sheets]
        (is (= loaded-csv-sheet csv-sheet))
        (is (= loaded-excel-sheet excel-sheet))))
    (testing "with-metadata-columns"
      (let [[csv-dataset excel-dataset] (open-all-datasets "./test/grafter" :metadata-fn with-metadata-columns)]
        (is (= (inc/$ 0 :file csv-dataset) "test.csv")
            "Should contain file name")

        (is (re-find #"/test/grafter" (inc/$ 0 :path excel-dataset))
            "Should contain file path")))))

(deftest make-dataset-tests
  (let [dataset (make-dataset csv-sheet)]
    (testing "make-dataset"
      (testing "makes incanter datasets."
        (is (= (inc/dataset? dataset))))

      (testing "Automatically assigns column names alphabetically if none are given"
        (let [columns (:column-names (make-dataset [(range 30)]))]
          (is (= "AA" (nth columns 26)))
          (is (= "AB" (nth columns 27))))))))

(deftest resolve-column-id-tests
  (testing "resolve-column-id"
    (let [dataset (test-dataset 5 5)]
      (are [expected lookup]
           (= expected (resolve-column-id dataset lookup :not-found))
           "A" "A"
           "A" :A
           "A" 0
           :not-found "Z"
           :not-found :Z))))

(deftest invalid-column-keys-tests
  (testing "invalid-column-keys"
    (let [dataset (test-dataset 5 5)]
      (testing "Returns the keys not in the dataset"
        (is (= ["X" "Z" 5 :F] (invalid-column-keys ["A" "B" "X" "Z" "C" "D" "E" 0 1 2 3 4 5 :A :B :C :D :E :F] dataset)))

        (testing "Preserves the order of invalid keys"
          (is (= ["Z" "X"] (invalid-column-keys ["A" "B" "Z" "X" "C" "D" "E"] dataset))))))))

(deftest columns-tests
  (let [expected-dataset (test-dataset 5 2)
        test-data (test-dataset 5 10)]
    (testing "columns"
      (testing "Narrows by string names"
        (is (= expected-dataset
               (columns test-data ["A" "B"]))  "Should select just columns A and B"))

      (testing "Narrows by numeric ids"
        (is (= expected-dataset
               (columns test-data [0 1])) "Should select columns 0 and 1 (A and B)"))

      (testing "Narrows by keywords"
        (is (= expected-dataset
               (columns test-data [:A :B])) "Should select columns 0 and 1 (A and B)"))

      (testing "works with infinite sequences"
        (is (columns test-data (grafter.sequences/integers-from 5))
            "Takes as much as it can from the supplied sequence and returns those columns.")

        (is (thrown? IndexOutOfBoundsException (columns test-data (range 10 100)))
            "Raises an exception if columns when paired with data are not actually column headings.")))))

(deftest all-columns-test
  (testing "all-columns"
    (let [test-data (test-dataset 5 5)]
      (is (thrown? IndexOutOfBoundsException
                   (all-columns test-data (range 100))))
      (testing "is the default"
        (is (thrown? IndexOutOfBoundsException
                     (all-columns test-data (range 100))))))))

(deftest rows-tests
  (let [test-data (test-dataset 10 2)]
    (testing "rows"
      (testing "works with infinite sequences"
        (is (= test-data (rows (test-dataset 10 2) (seqs/integers-from 0)))))

      (testing "pairing [5 6 7 8 9] with row numbers [0 1 2 3 4 5 6 7 8 9] returns rows [5 6 7 8 9]"
        (let [expected-dataset (make-dataset [[5 5]
                                              [6 6]
                                              [7 7]
                                              [8 8]
                                              [9 9]])]
          (is (= expected-dataset (rows test-data
                                        [5 6 7 8 9])))))

      (testing "allows returning multiple copies of consecutive rows"
        (let [expected-dataset (make-dataset [[2 2]
                                              [2 2]])]

          (is (= expected-dataset (rows test-data [2 2]))))

        (let [expected-dataset (make-dataset [[0 0]
                                              [1 1]
                                              [2 2]
                                              [2 2]])]

          (is (= expected-dataset (rows test-data [0 1 2 2]))))))))

(deftest drop-rows-test
  (testing "drop-rows"
    (let [dataset (test-dataset 3 1)]
      (is (= (make-dataset [[1] [2]]) (drop-rows dataset 1)))
      (is (= (make-dataset [[2]]) (drop-rows dataset 2)))
      (is (= (make-dataset []) (drop-rows dataset 1000))))))