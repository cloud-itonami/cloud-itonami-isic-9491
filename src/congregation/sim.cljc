(ns congregation.sim
  "Demo driver -- `clojure -M:dev:run`. Walks a clean matter through
  intake -> assessment verification -> safeguarding-concern screening
  -> pastoral-referral proposal (always escalates) -> human approval
  -> commit, then through doctrinal-statement proposal (always
  escalates) -> human approval -> commit, then shows five HARD holds
  (a jurisdiction with no spec-basis, a doctrinal statement whose
  topics exceed core doctrine, an unresolved safeguarding concern
  screened directly via `:safeguarding/screen` [never via an actuation
  op against an unscreened matter -- see this actor's own governor ns
  docstring / the lesson `parksafety`'s ADR-2607071922 Decision 5,
  `eldercare`'s, `museum`'s, `conservation`'s, `salon`'s,
  `entertainment`'s, `casework`'s, `hospital`'s, `facility`'s,
  `school`'s, `association`'s, `leasing`'s, `behavioral`'s,
  `secondary`'s, `card`'s, `water`'s, `telecom`'s, `aerospace`'s,
  `recovery`'s, `consulting`'s and `union`'s ADR-0001s already
  recorded], and a double pastoral-referral/doctrinal-statement of an
  already-processed matter) that never reach a human at all, and
  prints the audit ledger + the draft pastoral-referral and doctrinal-
  statement records."
  (:require [langgraph.graph :as g]
            [congregation.store :as store]
            [congregation.operation :as op]))

(def operator {:actor-id "op-1" :actor-role :clergy :phase 3})

(defn- exec! [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}} {:thread-id tid :resume? true}))

(defn -main [& _]
  (let [db (store/seed-db)
        actor (op/build db)]
    (println "== matter/intake matter-1 (JPN, clean; statement within core doctrine, no safeguarding concern) ==")
    (println (exec! actor "t1" {:op :matter/intake :subject "matter-1"
                                :patch {:id "matter-1" :congregant-name "Sakura Community Congregation"}} operator))

    (println "== assessment/verify matter-1 (escalates -- human approves) ==")
    (println (exec! actor "t2" {:op :assessment/verify :subject "matter-1"} operator))
    (println (approve! actor "t2"))

    (println "== safeguarding/screen matter-1 (clean; escalates -- human approves) ==")
    (println (exec! actor "t3" {:op :safeguarding/screen :subject "matter-1"} operator))
    (println (approve! actor "t3"))

    (println "== actuation/finalize-pastoral-referral matter-1 (always escalates -- actuation/finalize-pastoral-referral) ==")
    (let [r (exec! actor "t4" {:op :actuation/finalize-pastoral-referral :subject "matter-1"} operator)]
      (println r)
      (println "-- human clergy approves --")
      (println (approve! actor "t4")))

    (println "== actuation/publish-doctrinal-statement matter-1 (always escalates -- actuation/publish-doctrinal-statement) ==")
    (let [r (exec! actor "t5" {:op :actuation/publish-doctrinal-statement :subject "matter-1"} operator)]
      (println r)
      (println "-- human clergy approves --")
      (println (approve! actor "t5")))

    (println "== assessment/verify matter-2 (no spec-basis -> HARD hold) ==")
    (println (exec! actor "t6" {:op :assessment/verify :subject "matter-2" :no-spec? true} operator))

    (println "== assessment/verify matter-3 (escalates -- human approves; sets up the doctrine-exceeded test) ==")
    (println (exec! actor "t7" {:op :assessment/verify :subject "matter-3"} operator))
    (println (approve! actor "t7"))

    (println "== actuation/publish-doctrinal-statement matter-3 (:governance-succession outside core doctrine -> HARD hold) ==")
    (println (exec! actor "t8" {:op :actuation/publish-doctrinal-statement :subject "matter-3"} operator))

    (println "== safeguarding/screen matter-4 (unresolved -> HARD hold, never reaches a human) ==")
    (println (exec! actor "t9" {:op :safeguarding/screen :subject "matter-4"} operator))

    (println "== actuation/finalize-pastoral-referral matter-1 AGAIN (double-referral -> HARD hold) ==")
    (println (exec! actor "t10" {:op :actuation/finalize-pastoral-referral :subject "matter-1"} operator))

    (println "== actuation/publish-doctrinal-statement matter-1 AGAIN (double-publication -> HARD hold) ==")
    (println (exec! actor "t11" {:op :actuation/publish-doctrinal-statement :subject "matter-1"} operator))

    (println "== audit ledger ==")
    (doseq [f (store/ledger db)] (println f))

    (println "== draft pastoral-referral records ==")
    (doseq [r (store/referral-history db)] (println r))

    (println "== draft doctrinal-statement records ==")
    (doseq [r (store/statement-history db)] (println r))))
