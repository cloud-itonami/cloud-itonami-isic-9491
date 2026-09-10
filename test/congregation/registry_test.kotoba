(ns congregation.registry-test
  (:require [clojure.test :refer [deftest is]]
            [congregation.registry :as r]))

;; ----------------------------- doctrinal-statement-exceeds-core-doctrine? -----------------------------

(deftest not-exceeded-when-statement-is-a-subset-of-core-doctrine
  (is (not (r/doctrinal-statement-exceeds-core-doctrine? {:statement-topics #{:worship-schedule}
                                                          :core-doctrine-topics #{:worship-schedule :sacraments}})))
  (is (not (r/doctrinal-statement-exceeds-core-doctrine? {:statement-topics #{:worship-schedule :sacraments}
                                                          :core-doctrine-topics #{:worship-schedule :sacraments}})))
  (is (not (r/doctrinal-statement-exceeds-core-doctrine? {:statement-topics #{}
                                                          :core-doctrine-topics #{:worship-schedule}}))))

(deftest exceeded-when-statement-includes-a-topic-outside-core-doctrine
  (is (r/doctrinal-statement-exceeds-core-doctrine? {:statement-topics #{:worship-schedule :governance-succession}
                                                     :core-doctrine-topics #{:worship-schedule :sacraments}}))
  (is (r/doctrinal-statement-exceeds-core-doctrine? {:statement-topics #{:governance-succession}
                                                     :core-doctrine-topics #{:worship-schedule :sacraments}})))

(deftest exceeded-is-false-on-missing-fields
  (is (not (r/doctrinal-statement-exceeds-core-doctrine? {})))
  (is (not (r/doctrinal-statement-exceeds-core-doctrine? {:statement-topics #{:governance-succession}}))))

;; ----------------------------- register-pastoral-referral -----------------------------

(deftest referral-is-a-draft-not-a-real-referral
  (let [result (r/register-pastoral-referral "matter-1" "JPN" 0)]
    (is (nil? (get-in result ["certificate" "proof"])))
    (is (= (get-in result ["certificate" "issued_by_registry"]) false))
    (is (= (get-in result ["certificate" "status"]) "draft-unsigned"))))

(deftest referral-assigns-referral-number
  (let [result (r/register-pastoral-referral "matter-1" "JPN" 7)]
    (is (= (get result "referral_number") "JPN-REF-000007"))
    (is (= (get-in result ["record" "matter_id"]) "matter-1"))
    (is (= (get-in result ["record" "kind"]) "pastoral-referral-draft"))
    (is (= (get-in result ["record" "immutable"]) true))))

(deftest referral-validation-rules
  (is (thrown? Exception (r/register-pastoral-referral "" "JPN" 0)))
  (is (thrown? Exception (r/register-pastoral-referral "matter-1" "" 0)))
  (is (thrown? Exception (r/register-pastoral-referral "matter-1" "JPN" -1))))

;; ----------------------------- register-doctrinal-statement -----------------------------

(deftest statement-is-a-draft-not-a-real-publication
  (let [result (r/register-doctrinal-statement "matter-1" "JPN" 0)]
    (is (nil? (get-in result ["certificate" "proof"])))
    (is (= (get-in result ["certificate" "issued_by_registry"]) false))
    (is (= (get-in result ["certificate" "status"]) "draft-unsigned"))))

(deftest statement-assigns-statement-number
  (let [result (r/register-doctrinal-statement "matter-1" "JPN" 3)]
    (is (= (get result "statement_number") "JPN-DOC-000003"))
    (is (= (get-in result ["record" "matter_id"]) "matter-1"))
    (is (= (get-in result ["record" "kind"]) "doctrinal-statement-draft"))
    (is (= (get-in result ["record" "immutable"]) true))))

(deftest statement-validation-rules
  (is (thrown? Exception (r/register-doctrinal-statement "" "JPN" 0)))
  (is (thrown? Exception (r/register-doctrinal-statement "matter-1" "" 0)))
  (is (thrown? Exception (r/register-doctrinal-statement "matter-1" "JPN" -1))))

(deftest history-is-append-only
  (let [c1 (r/register-pastoral-referral "matter-1" "JPN" 0)
        hist (r/append [] c1)
        c2 (r/register-pastoral-referral "matter-2" "JPN" 1)
        hist2 (r/append hist c2)]
    (is (= 2 (count hist2)))
    (is (= "JPN-REF-000000" (get-in hist2 [0 "record_id"])))
    (is (= "JPN-REF-000001" (get-in hist2 [1 "record_id"])))))
