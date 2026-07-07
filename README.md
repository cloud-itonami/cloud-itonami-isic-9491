# cloud-itonami-isic-9491

Open Business Blueprint for **ISIC Rev.5 9491**: Activities of
religious organizations.

This repository publishes a congregation actor -- matter intake,
religious-organization administrative/safeguarding assessment,
safeguarding-concern screening, pastoral-care-referral finalization
and doctrinal-statement publication -- as an OSS business that any
qualified congregation operator can fork, deploy, run, improve and
sell, so a community or independent provider never surrenders
congregant data and ledgers to a closed SaaS.

Built on this workspace's
[`langgraph-clj`](https://github.com/com-junkawasaki/langgraph-clj)
StateGraph runtime (portable `.cljc`, supervised superstep loop,
interrupts, Datomic/in-mem checkpoints) -- the same actor pattern as
every prior actor in this fleet
([`cloud-itonami-isic-6511`](https://github.com/cloud-itonami/cloud-itonami-isic-6511),
[`6512`](https://github.com/cloud-itonami/cloud-itonami-isic-6512),
[`6621`](https://github.com/cloud-itonami/cloud-itonami-isic-6621),
[`6622`](https://github.com/cloud-itonami/cloud-itonami-isic-6622),
[`6629`](https://github.com/cloud-itonami/cloud-itonami-isic-6629),
[`6520`](https://github.com/cloud-itonami/cloud-itonami-isic-6520),
[`6530`](https://github.com/cloud-itonami/cloud-itonami-isic-6530),
[`6820`](https://github.com/cloud-itonami/cloud-itonami-isic-6820),
[`6612`](https://github.com/cloud-itonami/cloud-itonami-isic-6612),
[`6492`](https://github.com/cloud-itonami/cloud-itonami-isic-6492),
[`6920`](https://github.com/cloud-itonami/cloud-itonami-isic-6920),
[`6611`](https://github.com/cloud-itonami/cloud-itonami-isic-6611),
[`7120`](https://github.com/cloud-itonami/cloud-itonami-isic-7120),
[`8620`](https://github.com/cloud-itonami/cloud-itonami-isic-8620),
[`8530`](https://github.com/cloud-itonami/cloud-itonami-isic-8530),
[`9200`](https://github.com/cloud-itonami/cloud-itonami-isic-9200),
[`7500`](https://github.com/cloud-itonami/cloud-itonami-isic-7500),
[`9603`](https://github.com/cloud-itonami/cloud-itonami-isic-9603),
[`9521`](https://github.com/cloud-itonami/cloud-itonami-isic-9521),
[`9321`](https://github.com/cloud-itonami/cloud-itonami-isic-9321),
[`8730`](https://github.com/cloud-itonami/cloud-itonami-isic-8730),
[`9102`](https://github.com/cloud-itonami/cloud-itonami-isic-9102),
[`9103`](https://github.com/cloud-itonami/cloud-itonami-isic-9103),
[`9602`](https://github.com/cloud-itonami/cloud-itonami-isic-9602),
[`9000`](https://github.com/cloud-itonami/cloud-itonami-isic-9000),
[`8890`](https://github.com/cloud-itonami/cloud-itonami-isic-8890),
[`8610`](https://github.com/cloud-itonami/cloud-itonami-isic-8610),
[`9311`](https://github.com/cloud-itonami/cloud-itonami-isic-9311),
[`8510`](https://github.com/cloud-itonami/cloud-itonami-isic-8510),
[`9412`](https://github.com/cloud-itonami/cloud-itonami-isic-9412),
[`6491`](https://github.com/cloud-itonami/cloud-itonami-isic-6491),
[`8720`](https://github.com/cloud-itonami/cloud-itonami-isic-8720),
[`8521`](https://github.com/cloud-itonami/cloud-itonami-isic-8521),
[`6619`](https://github.com/cloud-itonami/cloud-itonami-isic-6619),
[`3600`](https://github.com/cloud-itonami/cloud-itonami-isic-3600),
[`6190`](https://github.com/cloud-itonami/cloud-itonami-isic-6190),
[`3030`](https://github.com/cloud-itonami/cloud-itonami-isic-3030),
[`3830`](https://github.com/cloud-itonami/cloud-itonami-isic-3830),
[`7020`](https://github.com/cloud-itonami/cloud-itonami-isic-7020),
[`9420`](https://github.com/cloud-itonami/cloud-itonami-isic-9420)) --
the FIRST congregational/religious-organization vertical in this
fleet. Here it is **CongregationOps-LLM ⊣ Congregational Governance
Governor**.

> **Why an actor layer at all?** An LLM is great at drafting a
> matter-intake summary, normalizing records, and checking whether a
> proposed public doctrinal statement's own topics actually stay
> within the congregation's own previously self-declared core-doctrine
> topics -- but it has **no notion of which jurisdiction's religious-
> organization administrative and safeguarding law is official, no
> license to finalize a real pastoral-care referral or publish a real
> public doctrinal statement, and no way to know on its own whether a
> safeguarding concern against a matter has actually stayed
> unresolved**. Letting it finalize a referral or publish a statement
> directly invites fabricated administrative citations, a doctrinal
> statement that invents positions the congregation never actually
> established, and an unresolved safeguarding concern being quietly
> overlooked -- and liability, and congregant-safety risk, for whoever
> runs it. This project seals the CongregationOps-LLM into a single
> node and wraps it with an independent **Congregational Governance
> Governor**, a human **approval workflow**, and an immutable **audit
> ledger**.

## Scope: what this actor does and does not do

This actor covers matter intake through administrative/safeguarding
assessment, safeguarding-concern screening, pastoral-care-referral
finalization and doctrinal-statement publication. It does **not**, by
itself, hold any registration required to operate as a religious
organization in a given jurisdiction, and it does not claim to. It
also does **not** model, judge or generate doctrine/theology itself --
`congregation.registry/doctrinal-statement-exceeds-core-doctrine?`
never evaluates theological correctness, only whether a proposed
statement's topics stay within the congregation's OWN previously self-
declared core-doctrine topic set (see that function's own docstring
for this honest, non-judging discipline). It also does not model a
real congregation-management system or the actual pastoral-care work
itself. Whoever deploys and operates a live instance (a registered
religious organization) supplies any jurisdiction-specific
registration, the real pastoral/theological expertise and the real
congregation-management integrations, and bears that jurisdiction's
liability -- the software supplies the governed, spec-cited, audited
execution scaffold so that organization does not have to build the
compliance layer from scratch.

### Actuation

**Finalizing a real pastoral-care referral or publishing a real public
doctrinal statement is never autonomous, at any phase, by
construction.** Two independent layers enforce this (`congregation.
governor`'s `:actuation/finalize-pastoral-referral`/`:actuation/
publish-doctrinal-statement` high-stakes gate and `congregation.
phase`'s phase table, which never puts `:actuation/finalize-pastoral-
referral`/`:actuation/publish-doctrinal-statement` in any phase's
`:auto` set) -- see `congregation.phase`'s docstring and `test/
congregation/phase_test.clj`'s `finalize-pastoral-referral-never-
auto-at-any-phase`/`publish-doctrinal-statement-never-auto-at-any-
phase`. The actor may draft, check and recommend; a human clergy/
religious-leadership member is always the one who actually finalizes a
referral or publishes a statement. Like `6512`/`6622`/`6520`/`6530`/
`6820`/`6920`/`6611`/`8530`/`9200`/`9521`/`8730`/`9102`/`9103`/`8890`/
`8610`/`8510`/`9412`/`8720`/`8521`/`6619`/`3600`/`6190`/`3030`/`3830`/
`9420`, this actor has TWO actuation events, both POSITIVE (issuing/
finalizing a real record), matching the majority pattern in this
fleet (`3600`/`6190` are the fleet's two NEGATIVE-actuation
exceptions).

## The core contract

```
matter intake + jurisdiction facts (congregation.facts, spec-cited)
        |
        v
   ┌──────────────┐   proposal      ┌───────────────────────┐
   │ Congregation-│ ─────────────▶ │ Congregational                │  (independent system)
   │ Ops-LLM      │  + citations    │ Governance Governor:         │
   │ (sealed)     │                 │ spec-basis · evidence-       │
   └──────────────┘         commit ◀────┼──────────▶ hold │ incomplete ·
                                 │             │           │ doctrinal-statement-
                           record + ledger  escalate ─▶ human   exceeds-core-doctrine
                                             (ALWAYS for         (subset) ·
                                              :actuation/finalize-       safeguarding-concern-
                                              pastoral-referral /        unresolved
                                              :actuation/publish-         (unconditional) ·
                                              doctrinal-statement)         already-referred/
                                                                            -published
```

**The CongregationOps-LLM never finalizes a referral or publishes a
statement the Congregational Governance Governor would reject, and
never does so without a human sign-off.** Hard violations (fabricated
administrative/safeguarding requirements; unsupported evidence; a
doctrinal statement exceeding core doctrine; an unresolved
safeguarding concern; a double referral or publication) force **hold**
and *cannot* be approved past; a clean referral/publication proposal
still always routes to a human.

## Run

```bash
clojure -M:dev:run     # walk one clean dual-actuation lifecycle + five HARD-hold cases through the actor
clojure -M:dev:test    # governor contract · phase invariants · store parity · registry conformance · facts coverage
clojure -M:lint        # clj-kondo (errors fail; CI mirrors this)
```

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot
performs the physical domain work**. Here a facility-access control
robot manages physical premises access where used, under the actor,
gated by the independent **Congregational Governance Governor**. The
governor never dispatches hardware itself; `:high`/`:safety-critical`
actions require human sign-off.

## Open business

This repository is not only source code. It is a public, forkable
business model:

| Layer | What is open |
|---|---|
| OSS core | Actor runtime, Congregational Governance Governor, pastoral-referral + doctrinal-statement draft records, audit ledger |
| Business blueprint | Customer, offer, pricing, unit economics, sales motion |
| Operator playbook | How to fork, license, deploy and support the service in a jurisdiction |
| Trust controls | Governance, security reporting, actuation invariant, audit requirements |

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md) to start this as an
open business on itonami.cloud, and
[`docs/adr/0001-architecture.md`](docs/adr/0001-architecture.md) for the
full architecture and decision record.

## Capability layer

This blueprint resolves its technology stack via
[`kotoba-lang/industry`](https://github.com/kotoba-lang/industry) (ISIC
`9491`). This vertical's service/member records are practice-specific
rather than a shared cross-operator data contract, so `congregation.*`
runs on the generic robotics/identity/forms/dmn/bpmn/audit-ledger
stack only -- no bespoke domain capability lib to reference at all.

## Layout

| File | Role |
|---|---|
| `src/congregation/store.cljc` | **Store** protocol -- `MemStore` ‖ `DatomicStore` (`langchain.db`) + append-only audit ledger + separate pastoral-referral/doctrinal-statement history. No dynamically-filed sub-record -- both actuation ops act directly on a pre-seeded matter, and the double-actuation guards check dedicated `:pastoral-referral-finalized?`/`:doctrinal-statement-published?` booleans rather than a `:status` value |
| `src/congregation/registry.cljc` | Pastoral-referral + doctrinal-statement draft records, plus `doctrinal-statement-exceeds-core-doctrine?` -- the FIFTH instance of this fleet's set-containment/subset check family (`registrar`/`casework`/`secondary`/`consulting` established the first four), never judging theological content itself |
| `src/congregation/facts.cljc` | Per-jurisdiction religious-organization administrative + safeguarding catalog with an official spec-basis citation per entry, honest coverage reporting |
| `src/congregation/congregationadvisor.cljc` | **CongregationOps-LLM** -- `mock-advisor` ‖ `llm-advisor`; intake/assessment/safeguarding-screening/pastoral-referral/doctrinal-statement proposals |
| `src/congregation/governor.cljc` | **Congregational Governance Governor** -- 4 HARD checks (spec-basis · evidence-incomplete · doctrinal-statement-exceeds-core-doctrine, pure ground-truth subset recompute · safeguarding-concern-unresolved, unconditional evaluation, the THIRTY-FIRST grounding of this discipline, distinct from `school`'s staff-background-check concept) + already-referred/already-published guards + 1 soft (confidence/actuation gate) |
| `src/congregation/phase.cljc` | **Phase 0→3** -- read-only → assisted intake → assisted verify → supervised (both pastoral-referral finalization and doctrinal-statement publication always human; matter intake is the ONLY auto-eligible op, no direct capital risk) |
| `src/congregation/operation.cljc` | **OperationActor** -- langgraph-clj StateGraph |
| `src/congregation/sim.cljc` | demo driver |
| `test/congregation/*_test.clj` | governor contract · phase invariants · store parity · registry conformance · facts coverage |

## Business-process coverage (honest)

This actor covers matter intake through administrative/safeguarding
assessment, safeguarding-concern screening, pastoral-care-referral
finalization and doctrinal-statement publication -- the core governed
lifecycle this blueprint's own `docs/business-model.md` names as its
Offer:

| Covered | Not covered (out of scope for this R0) |
|---|---|
| Matter intake + per-jurisdiction administrative/safeguarding checklisting, HARD-gated on an official spec-basis citation (`:matter/intake`/`:assessment/verify`) | Real congregation-management system integration, real pastoral-care casework itself (see `congregation.facts`'s docstring) |
| Safeguarding-concern screening, evaluated unconditionally so the screening op itself can HARD-hold on its own finding (`:safeguarding/screen`) | Any judgment of theological/doctrinal content itself -- deliberately outside this actor's competence |
| Pastoral-referral finalization, HARD-gated on full evidence, plus a double-finalization guard (`:actuation/finalize-pastoral-referral`) | |
| Doctrinal-statement publication, HARD-gated on full evidence and core-doctrine-scope sufficiency, plus a double-publication guard (`:actuation/publish-doctrinal-statement`) | |
| Immutable audit ledger for every intake/assessment/screening/referral/publication decision | |

Extending coverage is additive: add the next gate (e.g. a clergy-
credential-renewal check) as its own governed op with its own HARD
checks and tests, following the SAME "an independent governor
re-verifies against the actor's own records before any real-world act"
pattern this repo's flagship op already establishes.

## Jurisdiction coverage (honest)

`congregation.facts/coverage` reports how many requested jurisdictions
actually have an official spec-basis in `congregation.facts/catalog`
-- currently 4 seeded (JPN, USA, GBR, DEU) out of ~194 jurisdictions
worldwide. This is a starting catalog to prove the governor contract
end-to-end, not a claim of global coverage. Adding a jurisdiction is
additive: one map entry in `congregation.facts/catalog`, citing a real
official source -- never fabricate a jurisdiction's requirements to
make coverage look bigger.

## Maturity

`:implemented` -- `CongregationOps-LLM` + `Congregational Governance
Governor` run as real, tested code (see `Run` above), promoted from
the originally-published `:blueprint`-tier scaffold, modeled closely
on the forty-six prior actors' architecture. See `docs/adr/0001-
architecture.md` for the history and design.

## License

Code and implementation templates are AGPL-3.0-or-later.
