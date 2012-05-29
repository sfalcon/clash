(ns clash.test.core
  (:use [clash.core])
  (:use [clojure.test]))

(use '[clojure.java.io :only(reader delete-file)])

;; Test tools
(defn nano-to-millis
  [nt]
  (/ nt 1000000.0))

(defn elapsed
  [nt]
  (- (System/nanoTime) nt))

(defmacro nperf
  "Dump a message with the execution time in nano seconds."
  [exe, message]
  `(let [t# (System/nanoTime)
         result# ~exe]
     (println ~message "time(ns):" (elapsed t#))
     ; execute 'exe' here
     result#) )

(defn count-lines
  "How many lines in a file?"
  [file]
  (with-open [rdr (reader file)]
    (count (line-seq  rdr))) )

(def tresource
  "Define the current test directory."
  (str (System/getProperty "user.dir") "/test/clash/test"))

;; Tests
(deftest test-str-contains
   (is (not (str-contains? nil "o")))
   (is (not (str-contains? "foo" nil)))
   (is (not (str-contains? "foo" "g")))
   (is (str-contains? "foo" "o"))
   (is (not (str-contains? "foo" "|"))) )

(deftest test-prefix-command
  (is (= 3 (count (prefix-command "foo|bar"))))
  (is (= 6 (count (prefix-command "foobar")))) )

;; Test simple grep
(def input1 (str tresource "/input1.txt"))
(def output1 (str tresource "/output1.txt"))
(def command1 (str "grep message " input1))

;; Using (nperf) instead of (time)
(deftest test-jprocess-and-write
  (is (= 4 (count-lines input1)))
  (nperf (jprocess-and-write command1 output1 "\n") "Small file 'cl + grep' and dump")
  (is (= 3 (count-lines output1)))
  ; cleanup
  (delete-file output1) )

  