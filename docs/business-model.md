# Business Model: Activities of religious organizations

## Classification

- Repository: `cloud-itonami-isic-9491`
- ISIC Rev.5: `9491`
- Activity: activities of religious organizations -- worship, pastoral care, and religious community administration
- Social impact: community access, data sovereignty, transparent audit

## Customer

- independent congregations
- cooperative faith-community networks
- community religious-service programs

## Offer

- member/congregant intake
- service-schedule/program proposal
- pastoral-care-referral proposal
- immutable audit ledger

## Revenue

- self-host setup: one-time implementation fee
- managed hosting: monthly subscription per congregation
- support: monthly retainer with SLA
- migration: import from an incumbent congregation-management system
- per-member administration fee

## Trust Controls

- no pastoral-care referral or public doctrinal statement is finalized without human sign-off (clergy/religious leadership)
- a fabricated pastoral assessment forces a hold, not an override
- every referral path is auditable
- congregant data stays outside Git
- emergency manual override paths remain outside LLM control
- a fabricated administrative/safeguarding citation, incomplete
  evidence, a doctrinal statement exceeding the congregation's own
  core doctrine, or an unresolved safeguarding concern -- each forces
  a hold, not an override
- doctrinal-statement publication is logged and escalated, and cannot
  be finalized twice for the same matter: a double-publication attempt
  is held off this actor's own matter facts alone, with no upstream
  comparison needed
