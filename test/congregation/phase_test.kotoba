(ns congregation.phase-test
  "The phase table as executable tests. The invariant this repo cannot
  regress on: `:actuation/finalize-pastoral-referral`/`:actuation/
  publish-doctrinal-statement` must NEVER be a member of any phase's
  `:auto` set."
  (:require [clojure.test :refer [deftest is testing]]
            [congregation.phase :as phase]))

(deftest finalize-pastoral-referral-never-auto-at-any-phase
  (testing "structural invariant: no phase, now or in the future entries, auto-commits a real pastoral referral"
    (doseq [[n {:keys [auto]}] phase/phases]
      (is (not (contains? auto :actuation/finalize-pastoral-referral))
          (str "phase " n " must not auto-commit :actuation/finalize-pastoral-referral")))))

(deftest publish-doctrinal-statement-never-auto-at-any-phase
  (testing "structural invariant: no phase auto-commits a real doctrinal-statement publication"
    (doseq [[n {:keys [auto]}] phase/phases]
      (is (not (contains? auto :actuation/publish-doctrinal-statement))
          (str "phase " n " must not auto-commit :actuation/publish-doctrinal-statement")))))

(deftest safeguarding-screen-never-auto-at-any-phase
  (testing "screening carries no direct capital risk, but is still never auto-eligible, matching every sibling screening op in this fleet"
    (doseq [[n {:keys [auto]}] phase/phases]
      (is (not (contains? auto :safeguarding/screen))
          (str "phase " n " must not auto-commit :safeguarding/screen")))))

(deftest phase-0-is-fully-read-only
  (is (empty? (:writes (get phase/phases 0)))))

(deftest phase-3-auto-commits-only-no-capital-risk-ops
  (testing ":matter/intake carries no direct capital risk -- auto-eligible; it is the ONLY auto-eligible op in this domain"
    (is (= #{:matter/intake} (:auto (get phase/phases 3))))))

(deftest gate-hold-always-wins
  (is (= :hold (:disposition (phase/gate 3 {:op :matter/intake} :hold)))))

(deftest gate-escalates-a-clean-non-auto-write
  (is (= :escalate (:disposition (phase/gate 3 {:op :actuation/finalize-pastoral-referral} :commit))))
  (is (= :escalate (:disposition (phase/gate 3 {:op :actuation/publish-doctrinal-statement} :commit)))))

(deftest gate-holds-a-write-disabled-in-this-phase
  (is (= :hold (:disposition (phase/gate 0 {:op :matter/intake} :commit)))))
