# 0001 — Resolution Ledger Entries Do Not Reference the Qhorus Normative Record

Date: 2026-05-18
Status: Accepted

## Context and Problem Statement

`ProtocolDeviationLedgerEntry` records the PI authorisation lifecycle as a
tamper-evident Merkle chain. When resolution entries (APPROVED/REJECTED/
ESCALATED/EXPIRED) were introduced, the question arose whether each entry
should carry a reference to the corresponding qhorus Commitment or RESPONSE
message ID — to form an explicit link from the clinical audit record into the
normative layer.

## Decision Drivers

* The normative layer (qhorus) records speech acts independently; clinical records domain state
* The FDA audit trail must be complete within the clinical ledger chain itself
* The platform hypothesis is that the COMMAND/Commitment architecture adds structural value
  over a log — this hypothesis must be testable empirically

## Considered Options

* **Option A** — Include `commitmentId` in every `ProtocolDeviationLedgerEntry`
* **Option B** — Include qhorus message ID for PI response entries; no link for expiration
* **Option C** — No normative reference in ledger entries (chosen)

## Decision Outcome

Chosen option: **Option C**, because the normative link already exists through
`ProtocolDeviation.commitmentId`. Adding it to the ledger entry is a foreign key
annotation, not a cryptographic link — it does not merge the two Merkle chains,
and it does not advance the empirical test of the normative layer's value. The
normative layer's structural value is demonstrated by the architecture itself
(COMMAND/Commitment lifecycle, state machine, named obligation with deadline),
not by a UUID field in a join table.

### Positive Consequences

* Ledger entries remain self-contained domain records
* No schema coupling between clinical ledger and qhorus message IDs
* The normative hypothesis is tested through architecture and integration tests,
  not through annotation

### Negative Consequences / Tradeoffs

* An auditor reading only ledger entries must cross-reference `ProtocolDeviation`
  to find the qhorus Commitment
* If the ledger is ever exported as a standalone artifact, the normative link
  requires a join

## Pros and Cons of the Options

### Option A — `commitmentId` in every entry

* ✅ Makes normative link explicit in the audit artifact
* ❌ Foreign key annotation, not a cryptographic link — chains remain parallel, not merged
* ❌ Normative link duplicated (already in `ProtocolDeviation.commitmentId`)
* ❌ Does not advance the empirical proof of normative layer value

### Option B — Message ID for PI response entries only

* ✅ Links the specific speech act to the clinical record
* ❌ qhorus `MessageReceivedEvent` does not currently expose a message ID (blocked on qhorus#153)
* ❌ Expiration entries have no corresponding speech act — creates asymmetry in the chain

### Option C — No normative reference (chosen)

* ✅ Clean separation of concerns
* ✅ Normative link is stable and reachable through the domain entity
* ✅ The platform hypothesis is tested by the integration test, not by a schema field

## Links

* casehubio/qhorus#153 — MessageReceivedEvent CDI hook (would enable message ID if Option B revisited)
* `ProtocolDeviation.commitmentId` — the stable normative reference already in the domain entity
