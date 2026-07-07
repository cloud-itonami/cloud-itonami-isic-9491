# ADR-0001: CongregationOps-LLM ⊣ Congregational Governance Governor architecture

## Status

Accepted. `cloud-itonami-isic-9491` promoted from `:blueprint` to
`:implemented` in the `kotoba-lang/industry` registry.

## Context

`cloud-itonami-isic-9491` publishes an OSS business blueprint for
activities of religious organizations: worship, pastoral care, and
religious community administration, run by a qualified operator so a
community or independent provider never surrenders congregant data
and ledgers to a closed SaaS. Like every prior actor in this fleet,
the blueprint alone is not an implementation: this ADR records the
governed-actor architecture that promotes it to real, tested code,
following the same langgraph-clj StateGraph + independent Governor +
Phase 0→3 rollout pattern established by `cloud-itonami-isic-6511`
(life insurance) and applied across forty-six prior siblings, most
recently `cloud-itonami-isic-9420` (trade unions).

## Decision

### Decision 1: this fleet's FIRST congregational/religious-organization vertical

`cloud-itonami-isic-9491` is the first vertical in this fleet to model
a religious-organization administration domain. The design
deliberately confines itself to ADMINISTRATIVE and SAFEGUARDING
concerns (religious-corporation registration, child/vulnerable-person
protection) and never models, judges, or generates doctrine/theology
itself -- a boundary reflected in both `congregation.facts` (spec-
basis catalog cites only administrative/safeguarding law, never
doctrine) and `congregation.registry/doctrinal-statement-exceeds-
core-doctrine?` (a pure self-consistency check against the
congregation's OWN previously self-declared doctrine, never an
external judgment of theological correctness).

### Decision 2: entity and op shape

The primary entity is a `matter` (a congregational matter that may
involve a pastoral-care case and/or a doctrinal-statement concern,
analogous to `union.store`'s `dispute`). Five ops: `:matter/intake`
(directory upsert, no capital risk), `:assessment/verify` (per-
jurisdiction administrative/safeguarding evidence checklist, never
auto), `:safeguarding/screen` (safeguarding-concern screening,
unconditional-evaluation discipline, never auto), `:actuation/
finalize-pastoral-referral` (POSITIVE, high-stakes -- finalizing the
real referral), and `:actuation/publish-doctrinal-statement`
(POSITIVE, high-stakes -- publishing the real public doctrinal
statement). This matches the dual-actuation-on-one-entity shape every
recent dual-actuation sibling uses, grounded directly in this
blueprint's own README, business-model.md AND operator-guide.md, all
three of which consistently name exactly these two real-world acts.

### Decision 3: `doctrinal-statement-exceeds-core-doctrine?` -- the 5th set-containment check, 2nd permission-boundary polarity

Following `registrar.registry/prerequisites-satisfied?` (1st),
`casework.registry/eligibility-criteria-unsatisfied?` (2nd),
`secondary.registry/graduation-requirements-unsatisfied?` (3rd) and
`consulting.registry/engagement-scope-exceeded?` (4th, the first in
the "permission/boundary" polarity), `congregation.registry/
doctrinal-statement-exceeds-core-doctrine?` applies the SAME
`clojure.set/subset?` mechanism -- a proposed statement's own topics
must stay within the congregation's own self-declared core-doctrine
topics -- the SECOND instance of the permission/boundary polarity.
CRITICALLY, unlike a naive reading might suggest, this check NEVER
judges theological correctness: it is a pure self-consistency check
against the congregation's OWN prior self-declaration, the same
honest, non-fabricating discipline `congregation.facts` uses for
administrative law. This distinction is made explicit in the check's
own docstring and this ADR to avoid any implication that the actor
adjudicates doctrine.

### Decision 4: `safeguarding-concern-unresolved-violations` -- the 31st unconditional-evaluation screening grounding, distinct from school's background-check concept

Before finalizing this check's framing, `school.governor/background-
check-not-cleared-violations` was read directly (not just grepped) to
confirm the distinction: `school`'s check verifies whether a STAFF
MEMBER's own background-check clearance status is cleared; this
check instead verifies whether the MATTER/SITUATION itself carries an
unresolved safeguarding CONCERN (e.g. a pending allegation or risk
flag) -- a related but genuinely distinct real-world concept, the
same "verify precedent before claiming or denying it" discipline
`leasing`'s and `union`'s ADR-0001s establish. `safeguarding-concern-
unresolved-violations` reuses the unconditional-evaluation DISCIPLINE
(`casualty.governor/sanctions-violations`'s original fix) for the
31st distinct application overall, continuing the count established
across this window's builds (water=25th, telecom=26th, aerospace=
27th, recovery=28th, consulting=29th, union=30th, congregation=31st).
Exercised in tests/demo via `:safeguarding/screen` DIRECTLY against an
already-flagged matter, not via an actuation op against an unscreened
matter -- the "screen the screening op directly, not the actuation
op" lesson `parksafety`'s ADR-2607071922 Decision 5 established, now
applied for a TWENTY-FIRST consecutive sibling (`facility`=8th,
`school`=9th, `association`=10th, `leasing`=11th, `behavioral`=12th,
`secondary`=13th, `card`=14th, `water`=15th, `telecom`=16th,
`aerospace`=17th, `recovery`=18th, `consulting`=19th, `union`=20th,
`congregation`=21st).

### Decision 5: dedicated double-actuation-guard booleans

`:pastoral-referral-finalized?`/`:doctrinal-statement-published?` are
dedicated booleans on the `matter` record, never a single `:status`
value -- the same discipline every prior sibling governor's guards
establish, informed by `cloud-itonami-isic-6492`'s real status-
lifecycle bug (ADR-2607071320).

### Decision 6: Store protocol, MemStore + DatomicStore parity

`congregation.store/Store` is implemented by both `MemStore` (atom-
backed, default for dev/tests/demo) and `DatomicStore` (`langchain.
db`-backed), proven to satisfy the same contract in `test/
congregation/store_contract_test.clj` -- the same seam every sibling
actor uses so swapping the SSoT backend is a configuration change, not
a rewrite. `:statement-topics`/`:core-doctrine-topics` (Clojure sets)
are EDN-string-encoded on the Datomic-backed entity itself, the same
convention `consulting.store` established for compound entity fields.

### Decision 7: Phase 0→3 rollout

Phase 3's `:auto` set has exactly one member, `:matter/intake` (no
capital risk). `:assessment/verify` and `:safeguarding/screen` are
never auto-eligible at any phase (matching every sibling's screening-
op posture), and `:actuation/finalize-pastoral-referral`/`:actuation/
publish-doctrinal-statement` are permanently excluded from every
phase's `:auto` set -- a structural fact, not a rollout milestone,
enforced by BOTH `congregation.phase` and `congregation.governor`'s
`high-stakes` set independently.

### Decision 8: no bespoke domain capability lib

This vertical's service/member records are practice-specific rather
than a shared cross-operator data contract, so `congregation.*` runs
on the generic robotics/identity/forms/dmn/bpmn/audit-ledger stack
only -- the same posture `9412`/`8720`/`8521`/`3030`/`3830`/`7020`/
`9420` and others without a bespoke capability lib already establish.

### Decision 9: mock + LLM advisor pair

`congregation.congregationadvisor` provides `mock-advisor`
(deterministic, default everywhere -- the actor graph and governor
contract run offline) and `llm-advisor` (backed by `langchain.model/
ChatModel`, with a defensive EDN-proposal parser so a malformed LLM
response degrades to a safe low-confidence noop rather than ever
auto-finalizing a referral or auto-publishing a doctrinal statement;
the system prompt additionally explicitly instructs the model never
to judge or invent doctrinal content itself).

## Alternatives considered

- **Citing a specific denomination's safeguarding framework** (e.g. a
  particular church body's own child-protection charter) as the
  jurisdiction spec-basis. Rejected: this actor serves ANY religious
  organization in a jurisdiction, not one denomination -- citing
  government administrative/safeguarding law that applies broadly
  (religious-corporation registration statutes, general child/
  vulnerable-person protection law) keeps the catalog honest and non-
  preferential across denominations.
- **Modeling `doctrinal-statement-exceeds-core-doctrine?` as an
  external judgment of theological correctness.** Rejected outright --
  this would require the actor (or its governor) to adjudicate
  religious truth claims, categorically outside any government's or
  this actor's competence. The check is deliberately scoped to pure
  self-consistency against the congregation's OWN prior self-
  declaration.
- **Reusing `school.governor/background-check-not-cleared-violations`
  directly for the safeguarding concept.** Rejected after reading that
  check's actual implementation (not just its name): it verifies a
  STAFF MEMBER's clearance status, a different real-world fact than
  whether a MATTER itself carries an unresolved concern flag. Modeling
  this as a distinct concept (Decision 4) avoids conflating two
  genuinely different real-world facts under one label.

## Consequences

- Forty-seventh actor in this fleet (46 implemented before this
  build), and the FIRST congregational/religious-organization
  vertical.
- Confirms the set-containment/subset check family's permission/
  boundary polarity (established by `consulting`) generalizes to a
  second, genuinely distinct domain, while explicitly preserving the
  "never judge content, only self-consistency" discipline that makes
  this appropriate for a domain this sensitive.
- Demonstrates a careful precedent-distinction discipline: read
  `school`'s actual check implementation (not just its name) before
  deciding safeguarding-concern was a DIFFERENT concept, avoiding both
  a false-novelty claim and a false-reuse claim.
- `MemStore` ‖ `DatomicStore` parity is proven by `test/congregation/
  store_contract_test.clj`, the same `:db-api`-driven swap pattern
  every sibling actor uses.
- The safeguarding-concern test/demo correctly applied the established
  SCREENING-op-directly pattern for a twenty-first consecutive
  vertical -- further evidence that lessons recorded in this fleet's
  ADRs continue to transfer forward reliably.
- (-) This R0 seeds only 4 jurisdictions (JPN, USA, GBR, DEU) with an
  official spec-basis, out of ~194 worldwide; `congregation.facts/
  coverage` reports this honestly rather than claiming broader
  coverage.
- (-) This actor does not model a real congregation-management system,
  the actual pastoral-care casework itself, or any judgment of
  theological/doctrinal content -- see this repo's own README coverage
  table for the full honest-scope accounting.
