(ns congregation.store-contract-test
  "The Store contract, run against BOTH backends. Proving MemStore and
  the Datomic-backed (langchain.db) store satisfy the same contract is
  what makes 'swap the SSoT for Datomic / kotoba-server' a configuration
  change, not a rewrite -- see `cloud-itonami-isic-6511`'s
  `underwriting.store-contract-test` for the same pattern on the sibling
  actor."
  (:require [clojure.test :refer [deftest is testing]]
            [congregation.store :as store]))

(defn- backends []
  [["MemStore" (store/seed-db)] ["DatomicStore" (store/datomic-seed-db)]])

(deftest read-parity
  (doseq [[label s] (backends)]
    (testing label
      (is (= "Sakura Community Congregation" (:congregant-name (store/matter s "matter-1"))))
      (is (= "JPN" (:jurisdiction (store/matter s "matter-1"))))
      (is (= #{:worship-schedule} (:statement-topics (store/matter s "matter-1"))))
      (is (= #{:worship-schedule :sacraments} (:core-doctrine-topics (store/matter s "matter-1"))))
      (is (false? (:safeguarding-concern-unresolved? (store/matter s "matter-1"))))
      (is (= #{:worship-schedule :governance-succession} (:statement-topics (store/matter s "matter-3"))))
      (is (true? (:safeguarding-concern-unresolved? (store/matter s "matter-4"))))
      (is (false? (:pastoral-referral-finalized? (store/matter s "matter-1"))))
      (is (false? (:doctrinal-statement-published? (store/matter s "matter-1"))))
      (is (= ["matter-1" "matter-2" "matter-3" "matter-4"]
             (mapv :id (store/all-matters s))))
      (is (nil? (store/safeguarding-screen-of s "matter-1")))
      (is (nil? (store/assessment-of s "matter-1")))
      (is (= [] (store/ledger s)))
      (is (= [] (store/referral-history s)))
      (is (= [] (store/statement-history s)))
      (is (zero? (store/next-referral-sequence s "JPN")))
      (is (zero? (store/next-statement-sequence s "JPN")))
      (is (false? (store/matter-already-referred? s "matter-1")))
      (is (false? (store/matter-already-published? s "matter-1"))))))

(deftest write-and-ledger-parity
  (doseq [[label s] (backends)]
    (testing label
      (testing "partial upsert merges, preserving untouched fields"
        (store/commit-record! s {:effect :matter/upsert
                                 :value {:id "matter-1" :congregant-name "Sakura Community Congregation"}})
        (is (= "Sakura Community Congregation" (:congregant-name (store/matter s "matter-1"))))
        (is (= #{:worship-schedule} (:statement-topics (store/matter s "matter-1"))) "unrelated field preserved"))
      (testing "assessment / safeguarding-screen payloads commit and read back"
        (store/commit-record! s {:effect :assessment/set :path ["matter-1"]
                                 :payload {:jurisdiction "JPN" :checklist ["a" "b"]}})
        (is (= {:jurisdiction "JPN" :checklist ["a" "b"]} (store/assessment-of s "matter-1")))
        (store/commit-record! s {:effect :safeguarding-screen/set :path ["matter-1"]
                                 :payload {:matter-id "matter-1" :verdict :resolved}})
        (is (= {:matter-id "matter-1" :verdict :resolved} (store/safeguarding-screen-of s "matter-1"))))
      (testing "pastoral referral drafts a record and advances the sequence"
        (store/commit-record! s {:effect :matter/mark-referred :path ["matter-1"]})
        (is (= "JPN-REF-000000" (get (first (store/referral-history s)) "record_id")))
        (is (= "pastoral-referral-draft" (get (first (store/referral-history s)) "kind")))
        (is (true? (:pastoral-referral-finalized? (store/matter s "matter-1"))))
        (is (= 1 (count (store/referral-history s))))
        (is (= 1 (store/next-referral-sequence s "JPN")))
        (is (true? (store/matter-already-referred? s "matter-1")))
        (is (false? (store/matter-already-referred? s "matter-2"))))
      (testing "doctrinal statement drafts a record and advances the sequence"
        (store/commit-record! s {:effect :matter/mark-published :path ["matter-1"]})
        (is (= "JPN-DOC-000000" (get (first (store/statement-history s)) "record_id")))
        (is (= "doctrinal-statement-draft" (get (first (store/statement-history s)) "kind")))
        (is (true? (:doctrinal-statement-published? (store/matter s "matter-1"))))
        (is (= 1 (count (store/statement-history s))))
        (is (= 1 (store/next-statement-sequence s "JPN")))
        (is (true? (store/matter-already-published? s "matter-1")))
        (is (false? (store/matter-already-published? s "matter-2"))))
      (testing "ledger is append-only and order-preserving"
        (store/append-ledger! s {:op :a :disposition :commit})
        (store/append-ledger! s {:op :b :disposition :hold})
        (is (= [:commit :hold] (mapv :disposition (store/ledger s))))))))

(deftest datomic-empty-store-is-usable
  (let [s (store/datomic-store)]
    (is (nil? (store/matter s "nope")))
    (is (= [] (store/all-matters s)))
    (is (= [] (store/ledger s)))
    (is (= [] (store/referral-history s)))
    (is (= [] (store/statement-history s)))
    (is (zero? (store/next-referral-sequence s "JPN")))
    (is (zero? (store/next-statement-sequence s "JPN")))
    (store/with-matters s {"x" {:id "x" :congregant-name "n"
                                :statement-topics #{:worship-schedule}
                                :core-doctrine-topics #{:worship-schedule}
                                :safeguarding-concern-unresolved? false
                                :pastoral-referral-finalized? false :doctrinal-statement-published? false
                                :jurisdiction "JPN" :status :intake}})
    (is (= "n" (:congregant-name (store/matter s "x"))))))
