(ns congregation.governor
  "Congregational Governance Governor -- the independent compliance
  layer that earns the CongregationOps-LLM the right to commit. The
  LLM has no notion of religious-organization administrative law,
  whether a proposed doctrinal statement's own topics actually stay
  within the congregation's own previously self-declared core-doctrine
  topics, whether a safeguarding concern against a matter has actually
  stayed unresolved, or when an act stops being a draft and becomes a
  real-world pastoral-care referral or public doctrinal statement, so
  this MUST be a separate system able to *reject* a proposal and fall
  back to HOLD -- the congregation analog of `cloud-itonami-isic-
  6512`'s CasualtyGovernor.

  Six checks, in priority order, ALL HARD violations: a human approver
  CANNOT override them (you don't get to approve your way past a
  fabricated administrative spec-basis, incomplete evidence, a
  doctrinal statement exceeding core doctrine, an unresolved
  safeguarding concern, or a double referral/publication). The
  confidence/actuation gate is SOFT: it asks a human to look (low
  confidence / actuation), and the human may approve -- but see
  `congregation.phase`: for `:stake :actuation/finalize-pastoral-
  referral`/`:actuation/publish-doctrinal-statement` (a real
  congregational act) NO phase ever allows auto-commit either. Two
  independent layers agree that actuation is always a human call.

    1. Spec-basis                  -- did the assessment proposal cite
                                       an OFFICIAL administrative/
                                       safeguarding source
                                       (`congregation.facts`), or
                                       invent one?
    2. Evidence incomplete         -- for `:actuation/finalize-
                                       pastoral-referral`/`:actuation/
                                       publish-doctrinal-statement`,
                                       has the matter actually been
                                       assessed with a full
                                       congregant-intake-record/
                                       pastoral-assessment-record/
                                       safeguarding-clearance-record/
                                       clergy-credential-verification-
                                       record evidence checklist on
                                       file?
    3. Doctrinal statement exceeds
       core doctrine                  -- for `:actuation/publish-
                                       doctrinal-statement`,
                                       INDEPENDENTLY recompute whether
                                       the matter's own proposed
                                       statement topics stay within its
                                       own self-declared core-doctrine
                                       topics (`congregation.registry/
                                       doctrinal-statement-exceeds-
                                       core-doctrine?`) -- needs no
                                       proposal inspection or stored-
                                       verdict lookup at all, and never
                                       judges theological content
                                       itself. The FIFTH instance of
                                       this fleet's set-containment/
                                       subset check family
                                       (`registrar.governor/
                                       prerequisites-not-satisfied-
                                       violations`/`casework.governor/
                                       eligibility-criteria-
                                       unsatisfied-violations`/
                                       `secondary.governor/graduation-
                                       requirements-unsatisfied-
                                       violations`/`consulting.
                                       governor/engagement-scope-
                                       exceeded-violations` established
                                       the first four).
    4. Safeguarding concern
       unresolved                     -- reported by THIS proposal
                                       itself (a `:safeguarding/
                                       screen` that just found one),
                                       or already on file for the
                                       matter (`:safeguarding/screen`/
                                       `:actuation/finalize-pastoral-
                                       referral`). Evaluated
                                       UNCONDITIONALLY (not scoped to a
                                       specific op), the SAME
                                       discipline `casualty.governor/
                                       sanctions-violations`/...
                                       (thirty prior siblings, most
                                       recently `union.governor/
                                       compliance-flag-unresolved-
                                       violations`)... established --
                                       the THIRTY-FIRST distinct
                                       application of this exact
                                       discipline. Distinct from
                                       `school.governor/background-
                                       check-not-cleared-violations`
                                       (which verifies a STAFF MEMBER's
                                       own clearance status) -- this
                                       check instead verifies whether
                                       the MATTER/SITUATION itself
                                       carries an unresolved
                                       safeguarding concern (e.g. a
                                       pending allegation or risk
                                       flag), a related but distinct
                                       real-world concept. Exercised in
                                       tests/demo via `:safeguarding/
                                       screen` DIRECTLY, not via the
                                       actuation op against an
                                       unscreened matter -- see this
                                       ns's own test suite.
    5. Confidence floor / actuation
       gate                          -- LLM confidence below threshold,
                                       OR the op is `:actuation/
                                       finalize-pastoral-referral`/
                                       `:actuation/publish-doctrinal-
                                       statement` (REAL congregational
                                       acts) -> escalate.

  Two more guards, double-referral/double-publication prevention, are
  enforced but NOT listed as numbered HARD checks above because they
  need no upstream comparison at all -- `already-referred-violations`/
  `already-published-violations` refuse to finalize a referral/publish
  a statement for the SAME matter twice, off dedicated `:pastoral-
  referral-finalized?`/`:doctrinal-statement-published?` facts (never
  a `:status` value) -- the SAME 'check a dedicated boolean, not
  status' discipline every prior sibling governor's guards establish,
  informed by `cloud-itonami-isic-6492`'s status-lifecycle bug
  (ADR-2607071320)."
  (:require [congregation.facts :as facts]
            [congregation.registry :as registry]
            [congregation.store :as store]))

(def confidence-floor 0.6)

(def high-stakes
  "Stakes grave enough to always require a human, even when clean.
  Finalizing a real pastoral-care referral and publishing a real
  public doctrinal statement are the two real-world actuation events
  this actor performs -- a two-member set, matching every prior dual-
  actuation sibling's shape. Both are POSITIVE actuations (issuing/
  finalizing a record), matching this fleet's majority actuation
  shape."
  #{:actuation/finalize-pastoral-referral :actuation/publish-doctrinal-statement})

;; ----------------------------- checks -----------------------------

(defn- spec-basis-violations
  "A `:assessment/verify` (or actuation) proposal with no spec-basis
  citation is a HARD violation -- never invent a jurisdiction's
  religious-organization administrative or safeguarding requirements."
  [{:keys [op]} proposal]
  (when (contains? #{:assessment/verify :actuation/finalize-pastoral-referral :actuation/publish-doctrinal-statement} op)
    (let [value (:value proposal)]
      (when (or (empty? (:cites proposal))
                (and (contains? value :spec-basis) (nil? (:spec-basis value))))
        [{:rule :no-spec-basis
          :detail "公式spec-basisの引用が無い提案は宗教法人運営基準として扱えない"}]))))

(defn- evidence-incomplete-violations
  "For `:actuation/finalize-pastoral-referral`/`:actuation/publish-
  doctrinal-statement`, the jurisdiction's required congregant-intake-
  record/pastoral-assessment-record/safeguarding-clearance-record/
  clergy-credential-verification-record evidence must actually be
  satisfied -- do not trust the advisor's self-reported confidence
  alone."
  [{:keys [op subject]} st]
  (when (contains? #{:actuation/finalize-pastoral-referral :actuation/publish-doctrinal-statement} op)
    (let [m (store/matter st subject)
          assessment (store/assessment-of st subject)]
      (when-not (and assessment
                     (facts/required-evidence-satisfied?
                      (:jurisdiction m) (:checklist assessment)))
        [{:rule :evidence-incomplete
          :detail "法域の必要書類(会衆登録記録/牧会評価記録/セーフガーディング適格性記録/聖職者資格確認記録等)が充足していない状態での提案"}]))))

(defn- doctrinal-statement-exceeds-core-doctrine-violations
  "For `:actuation/publish-doctrinal-statement`, INDEPENDENTLY
  recompute whether the matter's own statement topics stay within its
  own core-doctrine topics via `congregation.registry/doctrinal-
  statement-exceeds-core-doctrine?` -- needs no proposal inspection or
  stored-verdict lookup at all, since its inputs are permanent ground-
  truth fields already on the matter."
  [{:keys [op subject]} st]
  (when (= op :actuation/publish-doctrinal-statement)
    (let [m (store/matter st subject)]
      (when (registry/doctrinal-statement-exceeds-core-doctrine? m)
        [{:rule :doctrinal-statement-exceeds-core-doctrine
          :detail (str subject " の提案トピック(" (:statement-topics m)
                      ")が既存教義範囲(" (:core-doctrine-topics m) ")を超過")}]))))

(defn- safeguarding-concern-unresolved-violations
  "An unresolved safeguarding concern -- reported by THIS proposal
  (e.g. a `:safeguarding/screen` that itself just found one), or
  already on file in the store for the matter (`:safeguarding/screen`/
  `:actuation/finalize-pastoral-referral`) -- is a HARD, un-
  overridable hold. Evaluated UNCONDITIONALLY (not scoped to a
  specific op) so the screening op itself can HARD-hold on its own
  finding."
  [{:keys [op subject]} proposal st]
  (let [hit-in-proposal? (= :unresolved (get-in proposal [:value :verdict]))
        matter-id (when (contains? #{:safeguarding/screen :actuation/finalize-pastoral-referral} op) subject)
        hit-on-file? (and matter-id (= :unresolved (:verdict (store/safeguarding-screen-of st matter-id))))]
    (when (or hit-in-proposal? hit-on-file?)
      [{:rule :safeguarding-concern-unresolved
        :detail "未解決のセーフガーディング懸念がある状態での牧会紹介確定提案は進められない"}])))

(defn- already-referred-violations
  "For `:actuation/finalize-pastoral-referral`, refuses to finalize a
  referral for the SAME matter twice, off a dedicated `:pastoral-
  referral-finalized?` fact (never a `:status` value)."
  [{:keys [op subject]} st]
  (when (= op :actuation/finalize-pastoral-referral)
    (when (store/matter-already-referred? st subject)
      [{:rule :already-referred
        :detail (str subject " は既に牧会紹介確定済み")}])))

(defn- already-published-violations
  "For `:actuation/publish-doctrinal-statement`, refuses to publish a
  statement for the SAME matter twice, off a dedicated `:doctrinal-
  statement-published?` fact (never a `:status` value)."
  [{:keys [op subject]} st]
  (when (= op :actuation/publish-doctrinal-statement)
    (when (store/matter-already-published? st subject)
      [{:rule :already-published
        :detail (str subject " は既に教義声明公開済み")}])))

(defn check
  "Censors a CongregationOps-LLM proposal against the governor rules.
  Returns {:ok? bool :violations [..] :confidence c :escalate? bool
  :high-stakes? bool :hard? bool}."
  [request _context proposal st]
  (let [hard (into []
                   (concat (spec-basis-violations request proposal)
                           (evidence-incomplete-violations request st)
                           (doctrinal-statement-exceeds-core-doctrine-violations request st)
                           (safeguarding-concern-unresolved-violations request proposal st)
                           (already-referred-violations request st)
                           (already-published-violations request st)))
        conf (:confidence proposal 0.0)
        low? (< conf confidence-floor)
        stakes? (boolean (high-stakes (:stake proposal)))
        hard? (boolean (seq hard))]
    {:ok?          (and (not hard?) (not low?) (not stakes?))
     :violations   hard
     :confidence   conf
     :hard?        hard?
     :escalate?    (and (not hard?) (or low? stakes?))
     :high-stakes? stakes?}))

(defn hold-fact
  "The audit fact written when a proposal is rejected (HOLD)."
  [request context verdict]
  {:t          :governor-hold
   :op         (:op request)
   :actor      (:actor-id context)
   :subject    (:subject request)
   :disposition :hold
   :basis      (mapv :rule (:violations verdict))
   :violations (:violations verdict)
   :confidence (:confidence verdict)})
