(ns congregation.governor-contract-test
  "The governor contract as executable tests -- the congregation
  analog of `cloud-itonami-isic-6512`'s `casualty.governor-contract-
  test`. The single invariant under test:

    CongregationOps-LLM never finalizes a pastoral referral or
    publishes a doctrinal statement the Congregational Governance
    Governor would reject, `:actuation/finalize-pastoral-referral`/
    `:actuation/publish-doctrinal-statement` NEVER auto-commit at any
    phase, `:matter/intake` (no direct capital risk) MAY auto-commit
    when clean, and every decision (commit OR hold) leaves exactly one
    ledger fact."
  (:require [clojure.test :refer [deftest is testing]]
            [langgraph.graph :as g]
            [congregation.store :as store]
            [congregation.operation :as op]))

(defn- fresh []
  (let [db (store/seed-db)]
    [db (op/build db)]))

(def operator {:actor-id "op-1" :actor-role :clergy :phase 3})

(defn- exec-op [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}} {:thread-id tid :resume? true}))

(defn- verify!
  "Walks `subject` through verify -> approve, leaving an assessment on
  file. Uses distinct thread-ids per call site by suffixing
  `tid-prefix`."
  [actor tid-prefix subject]
  (exec-op actor (str tid-prefix "-verify") {:op :assessment/verify :subject subject} operator)
  (approve! actor (str tid-prefix "-verify")))

(defn- screen!
  "Walks `subject` through safeguarding-concern screening -> approve,
  leaving a screening on file. Only safe to call for a matter whose
  concern status has already resolved -- an unresolved concern HARD-
  holds the screen itself (see
  `safeguarding-concern-is-held-and-unoverridable`)."
  [actor tid-prefix subject]
  (exec-op actor (str tid-prefix "-screen") {:op :safeguarding/screen :subject subject} operator)
  (approve! actor (str tid-prefix "-screen")))

(deftest clean-intake-auto-commits
  (let [[db actor] (fresh)
        res (exec-op actor "t1"
                  {:op :matter/intake :subject "matter-1"
                   :patch {:id "matter-1" :congregant-name "Sakura Community Congregation"}} operator)]
    (is (= :commit (get-in res [:state :disposition])))
    (is (= "Sakura Community Congregation" (:congregant-name (store/matter db "matter-1"))) "SSoT actually updated")
    (is (= 1 (count (store/ledger db))))))

(deftest assessment-verify-always-needs-approval
  (testing "verify is never in any phase's :auto set -- always human approval, even when clean"
    (let [[db actor] (fresh)
          res (exec-op actor "t2" {:op :assessment/verify :subject "matter-1"} operator)]
      (is (= :interrupted (:status res)))
      (let [r2 (approve! actor "t2")]
        (is (= :commit (get-in r2 [:state :disposition])))
        (is (some? (store/assessment-of db "matter-1")))))))

(deftest fabricated-jurisdiction-is-held
  (testing "an assessment/verify proposal with no official spec-basis -> HOLD, never reaches a human"
    (let [[db actor] (fresh)
          res (exec-op actor "t3"
                    {:op :assessment/verify :subject "matter-1" :no-spec? true} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:no-spec-basis} (-> (store/ledger db) first :basis)))
      (is (nil? (store/assessment-of db "matter-1")) "no assessment written"))))

(deftest finalize-pastoral-referral-without-assessment-is-held
  (testing "actuation/finalize-pastoral-referral before any assessment verification -> HOLD (evidence incomplete)"
    (let [[db actor] (fresh)
          res (exec-op actor "t4" {:op :actuation/finalize-pastoral-referral :subject "matter-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:evidence-incomplete} (-> (store/ledger db) first :basis))))))

(deftest doctrinal-statement-exceeds-core-doctrine-is-held
  (testing "a matter whose own proposed statement topics exceed its own core-doctrine topics -> HOLD"
    (let [[db actor] (fresh)
          _ (verify! actor "t5pre" "matter-3")
          res (exec-op actor "t5" {:op :actuation/publish-doctrinal-statement :subject "matter-3"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:doctrinal-statement-exceeds-core-doctrine} (-> (store/ledger db) last :basis)))
      (is (empty? (store/statement-history db))))))

(deftest safeguarding-concern-is-held-and-unoverridable
  (testing "an unresolved safeguarding concern on a matter -> HOLD, and never reaches request-approval -- exercised via :safeguarding/screen DIRECTLY, not via the actuation op against an unscreened matter (see this actor's governor ns docstring / parksafety's ADR-2607071922 Decision 5 / eldercare's, museum's, conservation's, salon's, entertainment's, casework's, hospital's, facility's, school's, association's, leasing's, behavioral's, secondary's, card's, water's, telecom's, aerospace's, recovery's, consulting's and union's ADR-0001s)"
    (let [[db actor] (fresh)
          res (exec-op actor "t6" {:op :safeguarding/screen :subject "matter-4"} operator)]
      (is (= :hold (get-in res [:state :disposition])) "settles immediately, no interrupt")
      (is (not= :interrupted (:status res)))
      (is (some #{:safeguarding-concern-unresolved} (-> (store/ledger db) first :basis)))
      (is (nil? (store/safeguarding-screen-of db "matter-4")) "no clearance written"))))

(deftest finalize-pastoral-referral-always-escalates-then-human-decides
  (testing "a clean, fully-assessed matter still ALWAYS interrupts for human approval -- actuation/finalize-pastoral-referral is never auto"
    (let [[db actor] (fresh)
          _ (verify! actor "t7pre" "matter-1")
          r1 (exec-op actor "t7" {:op :actuation/finalize-pastoral-referral :subject "matter-1"} operator)]
      (is (= :interrupted (:status r1)) "pauses for human approval even when governor-clean")
      (testing "approve -> commit, referral record drafted"
        (let [r2 (approve! actor "t7")]
          (is (= :commit (get-in r2 [:state :disposition])))
          (is (true? (:pastoral-referral-finalized? (store/matter db "matter-1"))))
          (is (= 1 (count (store/referral-history db))) "one draft referral record"))))))

(deftest publish-doctrinal-statement-always-escalates-then-human-decides
  (testing "a clean, fully-assessed, in-doctrine matter still ALWAYS interrupts for human approval -- actuation/publish-doctrinal-statement is never auto"
    (let [[db actor] (fresh)
          _ (verify! actor "t8pre" "matter-1")
          _ (screen! actor "t8pre2" "matter-1")
          r1 (exec-op actor "t8" {:op :actuation/publish-doctrinal-statement :subject "matter-1"} operator)]
      (is (= :interrupted (:status r1)) "pauses for human approval even when governor-clean")
      (testing "approve -> commit, statement record drafted"
        (let [r2 (approve! actor "t8")]
          (is (= :commit (get-in r2 [:state :disposition])))
          (is (true? (:doctrinal-statement-published? (store/matter db "matter-1"))))
          (is (= 1 (count (store/statement-history db))) "one draft statement record"))))))

(deftest finalize-pastoral-referral-double-referral-is-held
  (testing "finalizing the same matter's pastoral referral twice -> HOLD on the second attempt"
    (let [[db actor] (fresh)
          _ (verify! actor "t9pre" "matter-1")
          _ (exec-op actor "t9a" {:op :actuation/finalize-pastoral-referral :subject "matter-1"} operator)
          _ (approve! actor "t9a")
          res (exec-op actor "t9" {:op :actuation/finalize-pastoral-referral :subject "matter-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:already-referred} (-> (store/ledger db) last :basis)))
      (is (= 1 (count (store/referral-history db))) "still only the one earlier referral"))))

(deftest publish-doctrinal-statement-double-publication-is-held
  (testing "publishing the same matter's doctrinal statement twice -> HOLD on the second attempt"
    (let [[db actor] (fresh)
          _ (verify! actor "t10pre" "matter-1")
          _ (screen! actor "t10pre2" "matter-1")
          _ (exec-op actor "t10a" {:op :actuation/publish-doctrinal-statement :subject "matter-1"} operator)
          _ (approve! actor "t10a")
          res (exec-op actor "t10" {:op :actuation/publish-doctrinal-statement :subject "matter-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:already-published} (-> (store/ledger db) last :basis)))
      (is (= 1 (count (store/statement-history db))) "still only the one earlier publication"))))

(deftest every-decision-leaves-one-ledger-fact
  (testing "write-only-through-ledger: N operations -> N ledger facts"
    (let [[db actor] (fresh)]
      (exec-op actor "a" {:op :matter/intake :subject "matter-1"
                          :patch {:id "matter-1" :congregant-name "Sakura Community Congregation"}} operator)
      (exec-op actor "b" {:op :assessment/verify :subject "matter-1" :no-spec? true} operator)
      (is (= 2 (count (store/ledger db)))
          "one commit + one hold, both recorded"))))
