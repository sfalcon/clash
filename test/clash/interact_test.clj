;   Copyright (c) David Millett. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns clash.interact_test
  (:require [clash.text_tools :as tt])
  (:use [clojure.test]
        [clash.interact]
        [clash.command_test]
        [clash.tools]) )

;; ********************** Example Block ***********************************

(def simple-file (str tresource "/simple-structured.log"))

(def simple-stock-structure [:trade_time :action :stock :quantity :price])

; 05042013-13:24:12.000|sample-server|1.0.0|info|Buy,FOO,500,12.00
(def detailed-stock-pattern #"(\d{8}-\d{2}:\d{2}:\d{2}.\d{3})\|.*\|(\w*),(\w*),(\d*),(.*)")

(defn is-buy-or-sell?
  "If the current line contains 'Buy' or 'Sell'. "
  [line]
  (if (or (tt/str-contains? line "Buy") (tt/str-contains? line "Sell"))
    true
    false) )

(defn simple-stock-message-parser
  "An inexact split and parse of line text into 'simple-stock-structure'."
  [line]
  (let [splitsky (tt/split-with-regex line #"\|")
        date (first splitsky)
        message (last splitsky)
        corrected (str date "," message)]
    (tt/text-structure-to-map corrected #"," simple-stock-structure)) )

(defn better-stock-message-parser
  "An exact parsing of line text into 'simple-stock-structure' using
  'detailed-stock-pattern'."
  [line]
  (tt/regex-group-into-map line simple-stock-structure detailed-stock-pattern) )


(deftest test-atomic-map-from-file
  (let [result (atomic-map-from-file simple-file is-buy-or-sell? nil simple-stock-message-parser nil)]

    (is (= 6 (count @result)))
    ) )

(deftest test-atomic-list-from-file
  (let [result1 (atomic-list-from-file simple-file is-buy-or-sell? nil simple-stock-message-parser)]

    (is (= 6 (count @result1)))
    ) )

;; Note the count is 8 instead of 6 because the 'parser' function is more specific
(deftest test-atomic-list-from-fil__2_parameters_better_parser
  (let [result1 (atomic-list-from-file simple-file simple-stock-message-parser)
        result2 (atomic-list-from-file simple-file better-stock-message-parser)]

    (is (= 8 (count @result1)))
    (is (= 6 (count @result2)))
    ) )

(defn name?
  "A predicate to check 'stock' name against the current solution."
  [stock]
  #(= stock (-> % :stock)))

(defn action?
  "A predicate to check the 'buy' or 'sell' action of a stock."
  [action]
  #(= action (-> % :action)) )

(defn price-higher?
  "If a stock price is higher than X."
  [min]
  #(< min (read-string (-> % :price)) ) )

(defn price-lower?
  "If a stock price is lower than X."
  [max]
  #(> max (read-string (-> % :price)) ) )

(defn name-action?
  "A predicate to check 'stock' name and 'action' against the current solution."
  [stock action]
  #(and (= stock (-> % :stock)) (= action (-> % :action))) )

(defn name-action-every-pred?
  "A predicate to check 'stock' name and 'action' against the current solution,
  using 'every-pred'."
  [stock action]
  (every-pred (name? stock) #(= action (-> % :action))) )

(def increment-with-stock-quanity
  "Destructures 'solution' and existing 'count', and adds the stock 'quantity'
   'count'."
  (fn [solution count] (+ count (read-string (-> solution :quantity))) ) )

(deftest test-count-with-conditions
  (let [solutions (atomic-list-from-file simple-file better-stock-message-parser)]
    ;(println solutions)
    (are [x y] (= x y)
      0 (count-with-conditions @solutions #(= "XYZ" (-> % :stock)))
      6 (count-with-conditions @solutions nil)
      3 (count-with-conditions @solutions #(= "FOO" (-> % :stock)))
      3 (count-with-conditions @solutions (name? "FOO"))
      2 (count-with-conditions @solutions (name-action? "FOO" "Buy"))
      1 (count-with-conditions @solutions (name-action-every-pred? "FOO" "Sell"))
      ; any? and all?
      3 (count-with-conditions @solutions (all? (name? "FOO") (any? (action? "Sell") (action? "Buy"))) )
      1 (count-with-conditions @solutions (all? (name? "FOO") (price-higher? 12.1) (price-lower? 12.7)))
      ) ) )

;; Demonstrating custom increment
(deftest test-count-with-conditions__with_incrementer
  (let [solutions (atomic-list-from-file simple-file better-stock-message-parser)]
    (are [x y] (= x y)
      3 (count-with-conditions @solutions (name? "FOO") 0)
      1200 (count-with-conditions @solutions (name? "FOO") increment-with-stock-quanity 0)
      2470 (count-with-conditions @solutions nil increment-with-stock-quanity 20)
      ) ) )

;; Medium complexity structures
(def medium_complexity
  '({:foo "FOO" :bar {:zoo "ZOO" :fur (2 4)} }
     {:foo "BAR" :bar {:zoo "ZAP" :fur (3 5 7)} }) )

(defn is-zoo?
  [stock]
  (fn [solution] (= stock (-> solution :bar :zoo))) )

(def is-fur-odd?
  (fn [solution]
    (let [values (-> solution :bar :fur)]
      (every? odd? values)) ) )

(deftest test-count-with-conditions__medium_complexity
  (are [x y] (= x y)
    true ((is-zoo? "ZOO") (first medium_complexity))
    0 (count-with-conditions medium_complexity (is-zoo? "PIG"))
    1 (count-with-conditions medium_complexity (is-zoo? "ZOO"))
    1 (count-with-conditions medium_complexity is-fur-odd?)
    0 (count-with-conditions medium_complexity (every-pred is-fur-odd? (is-zoo? "BAR")))
    1 (count-with-conditions medium_complexity (every-pred is-fur-odd? (is-zoo? "ZAP")))
    ) )

;; Collecting results
(deftest test-collect-with-conditions
  (let [solutions (atomic-list-from-file simple-file better-stock-message-parser)]
    (are [x y] (= x y)
      0 (count (collect-with-condition @solutions (name? "XYZ")) )
      1 (count (collect-with-condition @solutions (name-action-every-pred? "FOO" "Sell")))
      2 (count (collect-with-condition @solutions (name-action? "FOO" "Buy")))
      6 (count (collect-with-condition @solutions nil))
      ) ) )

(deftest test-with-all-predicates
  (let [result1 (all-preds? 5 even?)
        result2 (all-preds? 4 even?)
        result3 (all-preds? 4 number? even?)
        result4 (all-preds? 4 number? odd?)
        result5 (all-preds? 12 number? even? #(= 0 (mod % 6)))]
    (is (not result1))
    (is result2)
    (is result3)
    (is (not result4))
    (is result5)
    ) )

(deftest test-with-any-predicates
  (let [result1 (any-preds? 5 even?)
        result2 (any-preds? 4 even?)
        result3 (any-preds? 4 number? even?)
        result4 (any-preds? 4 number? odd?)
        result5 (any-preds? 12 number? even? #(= 0 (mod % 5)))]
    (is (not result1))
    (is result2)
    (is result3)
    (is result4)
    (is result5)
    ) )

(defn divide-by-x?
  [x]
  #(= 0 (mod % x)) )

(deftest any-and-all?
  (let [result1 ((all? number? even?) 10)
        result2 ((all? number? odd?) 10)
        result3 ((any? number? even?) 11)
        result4 ((all? number? even? (divide-by-x? 5)) 10)
        result5 ((any? number? odd? even?) 16)
        result5 ((all? number? (any? (divide-by-x? 6) (divide-by-x? 4))) 16)]
    (is result1)
    (is (not result2))
    (is result3)
    (is result4)
    (is result5)
    )
  )