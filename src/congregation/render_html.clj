(ns congregation.render-html
  "Build-time HTML renderer for `docs/samples/operator-console.html`.

  Closes flagship checklist item 2 (com-junkawasaki/root ADR-2607189300):
  this repo previously had NO demo console and no generator at all. This
  namespace drives the REAL actor stack (`congregation.operation` ->
  `congregation.governor` -> `congregation.store`) via langgraph
  `g/run*`, through a scenario adapted from this repo's own
  `congregation.sim` demo driver (`clojure -M:dev:run`, confirmed BEFORE
  writing this file to run against the real seeded matter ids
  `matter-1`..`matter-4` -- this repo's sim driver uses ids that DO
  match `congregation.store/demo-data`, so it was safe to reuse rather
  than author from scratch).

  Everything on the rendered page is derived from a live run or from
  this repo's own source of truth:

    - the matter directory      -> `congregation.store/demo-data`
    - the governor HARD holds   -> the ledger this run actually produced
    - the action gate table     -> DERIVED from `congregation.phase/phases`
                                   and `congregation.governor/high-stakes`,
                                   not hand-typed, so the page cannot drift
                                   from the code it documents
    - the rollout phase ladder  -> `congregation.phase/phases`
    - the jurisdiction catalog  -> `congregation.facts/catalog` + `coverage`
    - approver attribution      -> DERIVED at render time by walking the
                                   committed registers (see `approval-rows`)
    - the registry drafts       -> `congregation.registry` output via the store

  No invented numbers, no mock rows, no timestamps in the page content:
  byte-identical across reruns against the same seed (verified by
  diffing two consecutive runs into scratch dirs).

  Usage: `clojure -M:dev:render-html [out-file]`
  (default `docs/samples/operator-console.html`)."
  (:require [jp-go-dds.skin]
            [clojure.string :as str]
            [congregation.facts :as facts]
            [congregation.governor :as governor]
            [congregation.operation :as op]
            [congregation.phase :as phase]
            [congregation.store :as store]
            [langgraph.graph :as g]))

(def ^:private operator
  "The same operator context this repo's own `congregation.sim` uses."
  {:actor-id "op-1" :actor-role :clergy :phase 3})

(defn- exec! [actor tid request]
  (g/run* actor {:request request :context operator} {:thread-id tid}))

(defn- approve!
  "Resume a paused actor run with a human approval, returning the graph
  result so the caller can harvest its `:audit` channel."
  [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}}
          {:thread-id tid :resume? true}))

(defn- approval-facts
  "The `:approval-granted` facts a graph run emitted. These live in the
  langgraph `:audit` channel and are NOT written to the store ledger
  (`congregation.operation`'s `:commit` node appends only the commit
  fact), which is exactly why approver attribution has to be joined
  from here rather than read back out of the ledger."
  [result]
  (filter #(= :approval-granted (:t %)) (-> result :state :audit)))

(defn run-demo!
  "Runs a fresh seeded store through a scenario mixing every disposition
  this actor can reach.

  matter-1 clears a full lifecycle -- intake (auto-commits at phase 3;
  it is the ONLY member of phase 3's `:auto` set), a JPN pastoral
  assessment (phase-gated, approved), a safeguarding screening (clean,
  approved), a pastoral-referral finalization (ALWAYS escalates --
  `:actuation/finalize-pastoral-referral` is never auto at any phase,
  approved) and a doctrinal-statement publication (ALWAYS escalates,
  same posture, approved).

  Then five HARD holds, none of which ever reaches a human:
    - matter-2 assessment cites no official spec-basis for its
      (deliberately unregistered) ATL jurisdiction      -> :no-spec-basis
    - matter-3 clears its own assessment (approved) but its proposed
      `:governance-succession` topic falls outside its own self-declared
      core doctrine  -> :doctrinal-statement-exceeds-core-doctrine
    - matter-4 screening itself detects an unresolved safeguarding
      concern                            -> :safeguarding-concern-unresolved
    - matter-1 referred a SECOND time                   -> :already-referred
    - matter-1 published a SECOND time                 -> :already-published

  Returns {:db store :approvals [..]} -- every field the renderer reads
  is real governor/store/langgraph output, not a hand-typed copy."
  []
  (let [db (store/seed-db)
        actor (op/build db)
        approvals (atom [])
        approve-and-record! (fn [tid]
                              (let [r (approve! actor tid)]
                                (swap! approvals into (approval-facts r))
                                r))]
    (exec! actor "t1" {:op :matter/intake :subject "matter-1"
                       :patch {:id "matter-1"
                               :congregant-name "Sakura Community Congregation"}})

    (exec! actor "t2" {:op :assessment/verify :subject "matter-1"})
    (approve-and-record! "t2")

    (exec! actor "t3" {:op :safeguarding/screen :subject "matter-1"})
    (approve-and-record! "t3")

    (exec! actor "t4" {:op :actuation/finalize-pastoral-referral :subject "matter-1"})
    (approve-and-record! "t4")

    (exec! actor "t5" {:op :actuation/publish-doctrinal-statement :subject "matter-1"})
    (approve-and-record! "t5")

    (exec! actor "t6" {:op :assessment/verify :subject "matter-2" :no-spec? true})

    (exec! actor "t7" {:op :assessment/verify :subject "matter-3"})
    (approve-and-record! "t7")

    (exec! actor "t8" {:op :actuation/publish-doctrinal-statement :subject "matter-3"})

    (exec! actor "t9" {:op :safeguarding/screen :subject "matter-4"})

    (exec! actor "t10" {:op :actuation/finalize-pastoral-referral :subject "matter-1"})

    (exec! actor "t11" {:op :actuation/publish-doctrinal-statement :subject "matter-1"})

    {:db db :approvals @approvals}))

;; ----------------------------- rendering helpers -----------------------------

(defn- esc [v]
  (-> (str v)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")))

(defn- kw-str [k] (if (keyword? k) (subs (str k) 1) (str k)))

(defn- topics
  "Render a topic set deterministically (sets have no reliable order)."
  [s]
  (if (seq s) (str/join ", " (sort (map kw-str s))) "—"))

(defn- td [& cells] (str "<tr>" (str/join (map #(str "<td>" % "</td>") cells)) "</tr>"))

(defn- table [headers rows]
  (str "    <table>\n"
       "      <thead><tr>"
       (str/join (map #(str "<th>" (esc %) "</th>") headers))
       "</tr></thead>\n"
       "      <tbody>\n"
       (str/join "\n" (map #(str "        " %) rows)) "\n"
       "      </tbody>\n"
       "    </table>\n"))

(defn- section [title lead body]
  (str "  <section class=\"card\">\n"
       "    <h2>" (esc title) "</h2>\n"
       "    <p class=\"muted\">" lead "</p>\n"
       body
       "  </section>\n"))

;; ----------------------------- section builders -----------------------------

(defn- last-fact-for [ledger id]
  (last (filter #(= (:subject %) id) ledger)))

(defn- status-cell [ledger id]
  (let [f (last-fact-for ledger id)]
    (cond
      (nil? f) "<span class=\"muted\">no activity</span>"
      (= :committed (:t f)) "<span class=\"ok\">committed</span>"
      (= :governor-hold (:t f))
      (str "<span class=\"critical\">HARD hold &middot; "
           (esc (kw-str (-> f :violations first :rule))) "</span>")
      :else "<span class=\"muted\">in progress</span>")))

(defn- matter-row [ledger {:keys [id congregant-name jurisdiction statement-topics
                                  core-doctrine-topics safeguarding-concern-unresolved?
                                  referral-number statement-number]}]
  (td (str "<code>" (esc id) "</code>")
      (esc congregant-name)
      (esc jurisdiction)
      (esc (topics statement-topics))
      (esc (topics core-doctrine-topics))
      (if safeguarding-concern-unresolved?
        "<span class=\"critical\">unresolved</span>"
        "<span class=\"ok\">none on file</span>")
      (if referral-number
        (str "<code>" (esc referral-number) "</code>")
        "<span class=\"muted\">—</span>")
      (if statement-number
        (str "<code>" (esc statement-number) "</code>")
        "<span class=\"muted\">—</span>")
      (status-cell ledger id)))

(defn- gate-rows
  "DERIVED from `congregation.phase/phases` + `congregation.governor/
  high-stakes` -- the page cannot drift from the code it documents."
  []
  (let [{:keys [writes auto]} (get phase/phases phase/default-phase)]
    (for [o (sort-by kw-str phase/write-ops)]
      (td (str "<code>" (esc o) "</code>")
          (cond
            (not (contains? writes o))
            "<span class=\"critical\">HOLD &middot; not enabled in this phase</span>"
            (contains? auto o)
            "<span class=\"ok\">auto-commit when governor-clean</span>"
            :else
            "<span class=\"warn\">human approval required</span>")
          (if (contains? governor/high-stakes o)
            "<span class=\"critical\">yes &middot; never auto at ANY phase</span>"
            "<span class=\"muted\">no</span>")))))

(defn- phase-rows []
  (for [[n {:keys [label writes auto]}] (sort-by key phase/phases)]
    (td (str "<code>" n "</code>"
             (when (= n phase/default-phase)
               " <span class=\"badge\">active</span>"))
        (esc label)
        (if (seq writes)
          (esc (str/join ", " (sort (map kw-str writes))))
          "<span class=\"muted\">none</span>")
        (if (seq auto)
          (esc (str/join ", " (sort (map kw-str auto))))
          "<span class=\"muted\">none</span>"))))

(defn- hold-rows [ledger]
  (for [{:keys [op subject violations confidence]}
        (filter #(= :governor-hold (:t %)) ledger)
        v violations]
    (td (str "<code>" (esc subject) "</code>")
        (str "<code>" (esc op) "</code>")
        (str "<span class=\"critical\">" (esc (kw-str (:rule v))) "</span>")
        (esc (:detail v))
        (esc confidence))))

(defn- jurisdiction-rows []
  (for [[iso3 {:keys [name owner-authority legal-basis provenance required-evidence]}]
        (sort-by key facts/catalog)]
    (td (str "<code>" (esc iso3) "</code>")
        (esc name)
        (esc owner-authority)
        (esc legal-basis)
        (str "<a href=\"" (esc provenance) "\">" (esc provenance) "</a>")
        (esc (count required-evidence)))))

(defn- approval-rows
  "Approver attribution, DERIVED at render time.

  `congregation.operation`'s `:request-approval` node attaches
  `:approved-by` to the record's `:payload`. Whether that survives into
  the SSoT depends on which effect the store's `commit-record!` runs --
  so this walks the committed registers and REPORTS WHAT IS ACTUALLY
  THERE rather than asserting a fixed answer. If the store is later
  changed to retain more (or less), this table self-corrects on the
  next build.

  Where the store did not keep the approver, the approver is still
  joined from the langgraph audit channel and labelled explicitly --
  silently omitting it would leave a reader unable to tell 'nobody
  approved' from 'the store didn't keep it'."
  [db approvals]
  (for [{:keys [op subject by]} approvals]
    (let [record (case op
                   :assessment/verify (store/assessment-of db subject)
                   :safeguarding/screen (store/safeguarding-screen-of db subject)
                   :actuation/finalize-pastoral-referral
                   (first (filter #(= subject (get % "matter_id")) (store/referral-history db)))
                   :actuation/publish-doctrinal-statement
                   (first (filter #(= subject (get % "matter_id")) (store/statement-history db)))
                   nil)
          retained (when (map? record)
                     (or (:approved-by record) (get record "approved_by")))]
      (td (str "<code>" (esc subject) "</code>")
          (str "<code>" (esc op) "</code>")
          (str "<code>" (esc by) "</code>")
          (if retained
            (str "<span class=\"ok\">retained in record &middot; <code>"
                 (esc retained) "</code></span>")
            (str "<span class=\"warn\">audit only — not retained in record</span>"))))))

(defn- registry-rows [db]
  (concat
   (for [r (store/referral-history db)]
     (td "<span class=\"badge\">pastoral referral</span>"
         (str "<code>" (esc (get r "record_id")) "</code>")
         (str "<code>" (esc (get r "matter_id")) "</code>")
         (esc (get r "jurisdiction"))
         (esc (get r "kind"))
         (esc (get r "immutable"))))
   (for [r (store/statement-history db)]
     (td "<span class=\"badge\">doctrinal statement</span>"
         (str "<code>" (esc (get r "record_id")) "</code>")
         (str "<code>" (esc (get r "matter_id")) "</code>")
         (esc (get r "jurisdiction"))
         (esc (get r "kind"))
         (esc (get r "immutable"))))))

(defn- ledger-rows [ledger]
  (for [{:keys [t op subject disposition basis]} ledger]
    (td (case t
          :committed "<span class=\"ok\">committed</span>"
          :governor-hold "<span class=\"critical\">governor-hold</span>"
          (str "<span class=\"muted\">" (esc (kw-str t)) "</span>"))
        (str "<code>" (esc op) "</code>")
        (str "<code>" (esc subject) "</code>")
        (esc (kw-str disposition))
        (esc (str/join ", " (map #(if (keyword? %) (kw-str %) (str %)) basis))))))

;; ----------------------------- document -----------------------------

(defn render
  "Renders the operator console from a completed `run-demo!` result."
  [{:keys [db approvals]}]
  (let [ledger (vec (store/ledger db))
        matters (store/all-matters db)
        cov (facts/coverage)
        holds (filter #(= :governor-hold (:t %)) ledger)]
    (str
     "<!DOCTYPE html>\n<html lang=\"en\"><head><meta charset=\"utf-8\">"
     "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1, viewport-fit=cover\">"
     "<title>cloud-itonami-isic-9491 &middot; congregation operator console</title><style>"
     (jp-go-dds.skin/dds+skin)
     "</style></head><body>\n"
     "<header class=\"bar\">\n"
     "  <h1>Activities of religious organizations (ISIC 9491) — Operator Console</h1>\n"
     "  <span class=\"badge\">read-only sample · governor-gated · pastoral referral and doctrinal statement always human-approved</span>\n"
     "</header>\n"
     "<main>\n"

     (section
      "Matter directory"
      (str "Demo snapshot — build-time-generated from <code>congregation.store</code> "
           "via <code>congregation.render-html</code> (<code>clojure -M:dev:render-html</code>). "
           "Every row is a real seeded matter; referral and statement numbers are produced by "
           "<code>congregation.registry</code> during this run, not typed in.")
      (table ["Matter" "Congregation" "Jurisdiction" "Proposed statement topics"
              "Self-declared core doctrine" "Safeguarding" "Referral no." "Statement no."
              "Last op status"]
             (map (partial matter-row ledger) matters)))

     (section
      "Action gate (Congregational Governance Governor, phase 3)"
      (str "Derived at build time from <code>congregation.phase/phases</code> and "
           "<code>congregation.governor/high-stakes</code> — this table is computed from the "
           "same code the actor runs, so it cannot drift. HARD violations cannot be overridden "
           "by an approver.")
      (table ["Op" "Gate at phase 3" "Real-world actuation?"] (gate-rows)))

     (section
      "Rollout phase ladder"
      (str "Read straight out of <code>congregation.phase/phases</code>. Note that "
           "<code>:actuation/finalize-pastoral-referral</code> and "
           "<code>:actuation/publish-doctrinal-statement</code> appear in no phase's auto set — "
           "including phase 3. That is a permanent structural fact, not a rollout milestone.")
      (table ["Phase" "Label" "Writes allowed" "Auto-commit allowed"] (phase-rows)))

     (section
      (str "Governor HARD holds this run (" (count holds) ")")
      (str "Every hold below was produced by the governor during this build's actor run and "
           "never reached a human. Each is un-overridable.")
      (table ["Matter" "Op" "Rule" "Detail" "Advisor confidence"] (hold-rows ledger)))

     (section
      "Approver attribution (derived)"
      (str "Approvals are granted in the langgraph <code>:audit</code> channel; whether the "
           "approver survives into the SSoT depends on which effect "
           "<code>congregation.store/commit-record!</code> runs. This table walks the committed "
           "registers and reports what is actually retained, so it self-corrects if the store "
           "changes. Where the record did not keep the approver, it is joined from the audit "
           "fact and labelled — an omission would be indistinguishable from nobody approving.")
      (table ["Matter" "Op" "Approved by (audit)" "Retained in SSoT record?"]
             (approval-rows db approvals)))

     (section
      "Registry drafts produced this run"
      (str "Built by <code>congregation.registry</code>. Every certificate this actor produces "
           "is UNSIGNED — signature is the congregation's own act, not this actor's.")
      (table ["Kind" "Record id" "Matter" "Jurisdiction" "Record kind" "Immutable"]
             (registry-rows db)))

     (section
      "Jurisdiction spec-basis catalog"
      (str "<code>congregation.facts/catalog</code> — the table the governor checks every "
           "assessment against. Coverage is reported honestly: "
           (esc (:covered cov)) " of " (esc (:requested cov)) " seeded jurisdictions have an "
           "official spec-basis. A jurisdiction absent from this table has NO spec-basis, full "
           "stop — which is exactly why <code>matter-2</code> (ATL) HARD-holds above. This "
           "catalog cites administrative-registration and safeguarding law only; it never models "
           "or judges doctrine.")
      (table ["ISO3" "Jurisdiction" "Owner authority" "Legal basis" "Provenance" "Required evidence"]
             (jurisdiction-rows)))

     (section
      (str "Audit ledger this run (" (count ledger) " facts)")
      "Append-only decision-fact log — every commit and hold this scenario produced."
      (table ["Fact" "Op" "Matter" "Disposition" "Basis"] (ledger-rows ledger)))

     "</main>\n"
     "<footer>\n"
     "  <p>Generated by <code>congregation.render-html</code> from a live "
     "<code>congregation.operation</code> actor run against "
     "<code>congregation.store/demo-data</code>. No mock rows, no hand-written HTML.</p>\n"
     "</footer>\n"
     "</body></html>\n")))

(defn -main [& args]
  (let [out (or (first args) "docs/samples/operator-console.html")
        {:keys [db] :as result} (run-demo!)
        ledger (store/ledger db)
        holds (filter #(= :governor-hold (:t %)) ledger)]
    ;; Build-time invariant, not a convention: a console that shows no HARD
    ;; hold has not demonstrated that the governor can actually refuse. If
    ;; the scenario ever stops producing one, fail the build rather than
    ;; publish a page that silently claims everything is fine.
    (when (zero? (count holds))
      (throw (ex-info "render-html: scenario produced 0 :governor-hold records -- refusing to write a console that does not demonstrate a HARD hold"
                      {:ledger-facts (count ledger)
                       :holds 0})))
    (let [html (render result)]
      (spit out html)
      (println "wrote" out
               (str "(" (count ledger) " ledger facts, "
                    (count holds) " HARD holds, "
                    (count (store/referral-history db)) " pastoral referrals, "
                    (count (store/statement-history db)) " doctrinal statements)")))))
