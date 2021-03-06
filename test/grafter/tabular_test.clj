(ns grafter.tabular-test
  (:require [clojure.test :refer :all]
            [grafter.sequences :as seqs]
            [grafter.tabular :refer :all]
            [incanter.core :as inc]))

(deftest header-functions-tests
  (let [raw-data [[:a :b :c] [1 2 3] [4 5 6]]]
    (testing "move-first-row-to-header"
      (let [retval (move-first-row-to-header raw-data)]
        (testing "returns a pair"
          (testing "where first item is the header"
            (is (= [:a :b :c] (first retval))))
          (testing "and the second item is the source data without the first row"
            (is (= (rest raw-data) (second retval)))))))))

(deftest make-dataset-tests
  (testing "make-dataset"
    (let [raw-data [[1 2 3] [4 5 6]]
          ds1 (make-dataset [[1 2][3 4]] ["a" "b"])
          ds2 (make-dataset [[1 2][3 4]] ["c" "d"])]

      (testing "converts a seq of seqs into a dataset"
        (is (instance? incanter.core.Dataset
                       (make-dataset raw-data))))

      (testing "assigns column names alphabetically by default"
        (let [header (:column-names (make-dataset raw-data))]
          (is (= ["a" "b" "c"] header))))

      (testing "takes a function that extracts the column names (header row)"
        (let [dataset (make-dataset raw-data move-first-row-to-header)
              header (:column-names dataset)]

          (is (= [1 2 3] header))))

      (testing "making a dataset from an existing dataset"
        (is (= ds1
               (make-dataset ds1))
            "Preserves data and column-names")
        (is (= ds2
               (make-dataset ds1 ["c" "d"])))))))

;;; These two vars define what the content of the files
;;; test/grafter/test.csv and test/grafter/test.xlsx should look like
;;; when loaded.
;;;
;;; - CSV data is always cast as Strings
;;; - Excel data when loaded is cast to floats

(def raw-csv-data [["one" "two" "three"]
                   ["1" "2" "3"]
                   ["4" "5" "6"]])

(def raw-txt-data [["a" "b" "c"]
                   ["1" "2" "3"]
                   ["4" "5" "6"]])

(def raw-excel-data [["one" "two" "three"]
                     [1.0 2.0 3.0]])

(def csv-sheet (make-dataset raw-csv-data move-first-row-to-header))

(def txt-sheet (make-dataset raw-txt-data move-first-row-to-header))

(def excel-sheet (make-dataset raw-excel-data move-first-row-to-header))

(deftest open-tabular-file-tests
  (testing "open-tabular-file"
    (testing "Opens CSV files"
      (let [loaded-csv (open-tabular-file "./test/grafter/test.csv")]
        (testing "are a seq of seqs"
          (is (= raw-csv-data loaded-csv)))))

    (testing "Opens CSV files with txt extensions"
      (let [loaded-txt (open-tabular-file "./test/grafter/test.txt" :ext :csv)]
        (testing "are a seq of seqs"
          (is (= raw-txt-data loaded-txt)))))

    (testing "Opens Excel files"
      (let [loaded-excel (open-tabular-file "./test/grafter/test.xlsx")]
        (testing "are incanter.core.Dataset records"
          (is (instance? org.apache.poi.xssf.usermodel.XSSFWorkbook loaded-excel)))))))

(deftest open-all-datasets-tests
  (testing "open-all-datasets"
    (let [sheets (open-all-datasets "./test/grafter" :make-dataset-fn #(make-dataset % move-first-row-to-header))]

      (is (seq? sheets) "should yield a seq")
      (is (= 2 (count sheets)) "should be 2 datasets")

      (let [read-data     (set sheets)
            expected-data (set [csv-sheet excel-sheet])]
        (is (= read-data expected-data))))

    (testing "with-metadata-columns"
      (let [[csv-dataset] (open-all-datasets "./test/grafter/test.csv" :metadata-fn with-metadata-columns)]
        (is (= (inc/$ 0 :file csv-dataset) "test.csv")
            "Should contain file name")

        (is (re-find #"/test/grafter" (inc/$ 0 :path csv-dataset))
            "Should contain file path")))))

(deftest make-dataset-tests
  (let [dataset (make-dataset csv-sheet)]
    (testing "make-dataset"
      (testing "makes incanter datasets."
        (is (inc/dataset? dataset)))

      (testing "Automatically assigns column names alphabetically if none are given"
        (let [columns (:column-names (make-dataset [(range 30)]))]
          (is (= "aa" (nth columns 26)))
          (is (= "ab" (nth columns 27))))))))

(deftest resolve-column-id-tests
  (testing "resolve-column-id"
    (let [dataset (test-dataset 5 5)]
      (are [expected lookup]
           (= expected (resolve-column-id dataset lookup :not-found))
           "a" "a"
           "a" :a
           "a" 0
           :not-found "z"
           :not-found :z))))

(deftest columns-tests
  (let [expected-dataset (test-dataset 5 2)
        test-data (test-dataset 5 10)]
    (testing "columns"
      (testing "Narrows by string names"
        (is (= expected-dataset
               (columns test-data ["a" "b"]))  "Should select just columns a and b"))

      (testing "Narrows by numeric ids"
        (is (= expected-dataset
               (columns test-data [0 1])) "Should select columns 0 and 1 (a and b)"))

      (testing "Narrows by keywords"
        (is (= expected-dataset
               (columns test-data [:a :b])) "should select columns 0 and 1 (a and b)"))

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

(deftest grep-test
  (let [dataset (make-dataset [["one" "two" "bar"]
                               ["foo" "bar" "b2az"]
                               ["foo" "blee" "bl3ah"]])

        expected-dataset (make-dataset [["foo" "bar" "b2az"]
                                        ["foo" "blee" "bl3ah"]])]

    (testing "grep"
      (testing "with a function"
        (testing "receives a single cell as its argument"
          (grep dataset (fn [cell]
                          (is (= String (class cell))))))

        (is (= expected-dataset
               (grep dataset (fn [cell]
                               (= cell "foo")))))

        (is (= expected-dataset
               (grep dataset (fn [cell]
                               (.startsWith cell "f")))))


        (let [expected (make-dataset [["one" "two" "bar"]])]
          (is (= expected
                 (grep dataset (fn [cell]
                                 (= cell "bar")) ["c"])))))
      (testing "with a string"
        (is (= expected-dataset
               (grep dataset "fo"))))

      (testing "with a regex"
        (is (= expected-dataset
               (grep dataset #"\d"))))

      (testing "on an empty dataset"
        (let [empty-ds (make-dataset)]
          (is (= empty-ds
                 (grep empty-ds #"foo"))))))))

(deftest mapc-test
  (let [dataset (make-dataset [[1 2 "foo" 4]
                               [5 6 "bar" 8]
                               [9 10 "baz" 12]])

        fs {"a" str, "b" inc, "c" identity, "d" inc}
        fs-incomplete {"a" str, "b" inc, "d" inc}
        fs-vec [str inc identity inc]
        expected-dataset (make-dataset [["1" 3 "foo" 5]
                                        ["5" 7 "bar" 9]
                                        ["9" 11 "baz" 13]])]
    (testing "mapc with a hashmap"
      (testing "complete hashmap"
        (is (= expected-dataset
               (mapc dataset fs))))
      (testing "incomplete hashmap implies mapping identity for unspecified columns"
        (is (= expected-dataset
               (mapc dataset fs-incomplete)))
        (is (= dataset
               (mapc dataset {})))))
    (testing "mapc with a vector of functions works positionally"
      (is (= expected-dataset
             (mapc dataset fs-vec))))
    (testing "incomplete vector implies mapping identity over unspecified columns"
      (let [dataset (make-dataset [[1 2 "foo" 4]])
            expected (make-dataset [["1" 2 "foo" 4]])]
        (is (= expected
               (mapc dataset  [str])))))))


(deftest swap-test
  (let [ordered-ds (make-dataset
                    [["a" "b" "c" "d"]
                     ["a" "b" "c" "d"]
                     ["a" "b" "c" "d"]])]
    (testing "swaping two columns"
      (is (= (make-dataset
              [["b" "a" "c" "d"]
               ["b" "a" "c" "d"]
               ["b" "a" "c" "d"]]
              ["b" "a" "c" "d"])
             (swap ordered-ds "a" "b"))))
    (testing "swaping two times two columns"
      (is (= (make-dataset
              [["b" "c" "a" "d"]
               ["b" "c" "a" "d"]
               ["b" "c" "a" "d"]]
              ["b" "c" "a" "d"])
             (swap ordered-ds "a" "b" "a" "c"))))
    (testing "swaping odd number of columns"
      (is (thrown? java.lang.Exception
             (swap ordered-ds "a" "b" "c"))))))

(deftest derive-column-test
  (let [subject (make-dataset [[1 2] [3 4]])
        expected (make-dataset [[1 2 3] [3 4 7]])]

    (is (= (derive-column subject "c" ["a" "b"] +)
           expected))

    (is (= (derive-column subject "c" [:a :b] +)
           expected))

    (is (= (derive-column subject "c" :a str)
           (make-dataset [[1 2 "1"] [3 4 "3"]])))))

(deftest add-columns-test
  (let [subject (make-dataset [[1 2 3] [4 5 6]])]
    (testing "add-columns"
      (testing "with hash-map"
        (is (= (make-dataset [[1 2 3 "kitten" "trousers"]
                              [4 5 6 "kitten" "trousers"]]
                             ["a" "b" "c" "animal" "clothes"])

               (add-columns subject {"animal" "kitten" "clothes" "trousers"}))
            "adds cells to every row of the specified columns"))

      (testing "with function"
        (testing "with 1 argument"

          (let [foo-bar (fn [v]
                          {"foo" v "bar" v})]
            (is (= (make-dataset [[1 2 3 1 1]
                                  [4 5 6 4 4]]
                                 ["a" "b" "c" "foo" "bar"])

                   (add-columns subject ["a"] foo-bar)
                   (add-columns subject "a" foo-bar))

                "When given a function and a selection of column ids - it applies ƒ to the source cells, and merges the returned map into the cell")))

        (testing "with multiple arguments"
          (let [expected (make-dataset [[1 2 3 3 0]
                                        [4 5 6 9 3]]
                                       ["a" "b" "c" :d :e])]
            (is (= expected (add-columns subject [:a "b"]
                                         (fn [a b] {:d (+ a b) :e (dec a)}))))))))))

(deftest build-lookup-table-test
  (let [debts (make-dataset [["rick"  25     33]
                             ["john"  9      12]
                             ["bob"   48     20]
                             ["kevin" 43     10]]
                            ["name" "age" "debt"])]

    (testing "with no specified return column return a map of all columns except the chosen key"
      (is (= {"age" 25 "debt" 33}
             ((build-lookup-table debts "name") "rick")
             ((build-lookup-table debts ["name"]) "rick"))))

    (testing "1 key column"
      (is (= {"debt" 20}
             ((build-lookup-table debts "name" ["debt"]) "bob")
             ((build-lookup-table debts "name" "debt") "bob")))

      (is (= {"age" 48}
             ((build-lookup-table debts "name" "age") "bob")))

      (is (= nil
             ((build-lookup-table debts "name" "debt") "foo"))))

    (testing "many explicit return value columns"
      (is (= {"debt" 20, "age" 48, "name" "bob"}
             ((build-lookup-table debts ["name"] ["name" "age" :debt]) "bob"))))

    (testing "composite key columns"
      (is (= {"debt" 33}
             ((build-lookup-table debts ["name" "age"] "debt") ["rick" 25])))
      (is (= nil
             ((build-lookup-table debts ["name" "age"] "debt") ["foo" 99]))))


    ;; TODO when we find a better error handling approach we should
    ;; support it here too.
    (testing "errors"
      (testing "no key column"
        (is (thrown? IndexOutOfBoundsException
                     ((build-lookup-table debts [] "debt") "bob"))))
      (testing "key column not existing"
        (is (thrown? IllegalArgumentException
                     ((build-lookup-table debts "foo" "debt") "bob")))))))

(deftest add-columns-with-lookup-table-test
  (testing "add-columns and build-lookup-table compose nicely"

    (let [customer (make-dataset [[1 "bob" "hope"] [2 "john" "doe"] [3 "jane" "blogs"]]
                                 ["id" "fname" "sname"])

          accounts (make-dataset [[123 1 32]
                                  [124 3 300]
                                  [125 2 -500]]
                                 ["account" "customer-id" "balance"])]

      (-> customer
          (add-columns "id" (build-lookup-table accounts ["customer-id"]))))))
