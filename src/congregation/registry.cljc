(ns congregation.registry
  "Pure-function pastoral-care-referral + doctrinal-statement record
  construction -- an append-only congregation book-of-record draft.

  Like every sibling actor's registry, there is no single
  international check-digit standard for a pastoral-care-referral or
  doctrinal-statement reference number -- every congregation/
  jurisdiction assigns its own reference format. This namespace does
  NOT invent one; it builds a jurisdiction-scoped sequence number and
  validates the record's required fields, the same honest, non-
  fabricating discipline `congregation.facts` uses.

  `doctrinal-statement-exceeds-core-doctrine?` is the FIFTH instance
  of this fleet's set-containment/subset check family (`registrar.
  registry/prerequisites-satisfied?`/`casework.registry/eligibility-
  criteria-unsatisfied?`/`secondary.registry/graduation-requirements-
  unsatisfied?` established the first three, `consulting.registry/
  engagement-scope-exceeded?` the fourth in the 'permission/boundary'
  polarity), applying the SAME `clojure.set/subset?` mechanism -- a
  PROPOSED statement's own recorded `:statement-topics` must stay a
  subset of the congregation's own recorded `:core-doctrine-topics` --
  the SECOND instance of the permission/boundary polarity `consulting`
  established. CRITICALLY, this check NEVER judges theological
  correctness or doctrinal content itself (outside any government's or
  this actor's competence); it only verifies self-consistency against
  the congregation's OWN previously self-declared doctrine topic set,
  the same honest, non-judging discipline `congregation.facts` uses
  for administrative law.

  This namespace is pure data + pure functions -- no I/O, no network
  call to any real congregation-management system. It builds the
  RECORD a congregation would keep, not the act of finalizing the
  referral or publishing the statement itself (that is `congregation.
  operation`'s `:actuation/finalize-pastoral-referral`/`:actuation/
  publish-doctrinal-statement`, always human-gated -- see README
  `Actuation`)."
  (:require [clojure.set :as set]
            [clojure.string :as str]))

(defn- unsigned-certificate
  "Every certificate this actor produces is UNSIGNED -- signature is the
  congregation's own act, not this actor's. See README `Actuation`."
  [kind subject record-id]
  {"@context" ["https://www.w3.org/ns/credentials/v2"]
   "type" ["VerifiableCredential" kind]
   "credentialSubject" {"id" subject "record" record-id}
   "proof" nil
   "issued_by_registry" false
   "status" "draft-unsigned"})

(defn- zero-pad [n w]
  (let [s (str n)]
    (str (apply str (repeat (max 0 (- w (count s))) "0")) s)))

(defn doctrinal-statement-exceeds-core-doctrine?
  "Does `matter`'s own `:statement-topics` set contain any topic NOT in
  its own `:core-doctrine-topics` set? A pure ground-truth check
  against the matter's own permanent fields -- no upstream comparison
  needed, and no judgment of theological content. The FIFTH instance
  of this fleet's set-containment/subset check family (see ns
  docstring)."
  [{:keys [statement-topics core-doctrine-topics]}]
  (and (set? statement-topics) (set? core-doctrine-topics)
       (not (set/subset? statement-topics core-doctrine-topics))))

(defn register-pastoral-referral
  "Validate + construct the PASTORAL-CARE-REFERRAL registration DRAFT
  -- the congregation's own act of finalizing a real referral for a
  congregant to pastoral care or support. Pure function -- does not
  touch any real congregation-management system; it builds the RECORD
  a congregation would keep. `congregation.governor` independently re-
  verifies the matter's own safeguarding-concern resolution status,
  and blocks a double-finalization for the same matter, before this is
  ever allowed to commit."
  [matter-id jurisdiction sequence]
  (when-not (and matter-id (not= matter-id ""))
    (throw (ex-info "pastoral-referral: matter_id required" {})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info "pastoral-referral: jurisdiction required" {})))
  (when (< sequence 0)
    (throw (ex-info "pastoral-referral: sequence must be >= 0" {})))
  (let [referral-number (str (str/upper-case jurisdiction) "-REF-" (zero-pad sequence 6))
        record {"record_id" referral-number
                "kind" "pastoral-referral-draft"
                "matter_id" matter-id
                "jurisdiction" jurisdiction
                "immutable" true}]
    {"record" record "referral_number" referral-number
     "certificate" (unsigned-certificate "PastoralReferral" referral-number referral-number)}))

(defn register-doctrinal-statement
  "Validate + construct the DOCTRINAL-STATEMENT registration DRAFT --
  the congregation's own act of publishing a real public doctrinal
  statement. Pure function -- does not touch any real congregation-
  management system; it builds the RECORD a congregation would keep.
  `congregation.governor` independently re-verifies the matter's own
  statement-topics stay within its own core-doctrine topics, and
  blocks a double-publication for the same matter, before this is ever
  allowed to commit."
  [matter-id jurisdiction sequence]
  (when-not (and matter-id (not= matter-id ""))
    (throw (ex-info "doctrinal-statement: matter_id required" {})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info "doctrinal-statement: jurisdiction required" {})))
  (when (< sequence 0)
    (throw (ex-info "doctrinal-statement: sequence must be >= 0" {})))
  (let [statement-number (str (str/upper-case jurisdiction) "-DOC-" (zero-pad sequence 6))
        record {"record_id" statement-number
                "kind" "doctrinal-statement-draft"
                "matter_id" matter-id
                "jurisdiction" jurisdiction
                "immutable" true}]
    {"record" record "statement_number" statement-number
     "certificate" (unsigned-certificate "DoctrinalStatement" statement-number statement-number)}))

(defn append [history result]
  (conj (vec history) (get result "record")))
