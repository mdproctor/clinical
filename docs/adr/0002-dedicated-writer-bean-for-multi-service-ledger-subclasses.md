# 0002 — Dedicated Writer Bean for Multi-Service LedgerEntry Subclasses

Date: 2026-05-18
Status: Accepted

## Context and Problem Statement

`ProtocolDeviationLedgerEntry` is written from three services:
`ProtocolDeviationService` (COMMAND), `PiResponseListener` (PI response), and
`DeviationExpirationJob` (expiration). Each must compute `sequenceNumber` as
`findLatestBySubjectId(deviationId).sequenceNumber + 1`. Without a shared owner,
each service reads the latest independently with no single place to test the
invariant or ensure consistent entry construction.

## Decision Drivers

* `sequenceNumber` chain integrity must hold across all three write sites
* The invariant must be testable in isolation (unit-testable without Quarkus)
* Entry construction logic (field population, actor roles, type mapping) should
  not be duplicated across services

## Considered Options

* **Option A** — Each service owns its own ledger entry construction and sequence computation
* **Option B** — Dedicated `@ApplicationScoped` writer bean per subclass (chosen)
* **Option C** — Separate `ProtocolDeviationCommandEntry` and `ProtocolDeviationResolutionEntry` subclasses, one writer each

## Decision Outcome

Chosen option: **Option B**, because it centralises sequence computation and
entry construction in a single, testable unit (`DeviationLedgerWriter`) while
keeping a single entity class for the full lifecycle. The writer is unit-testable
with a mocked repository; the sequenceNumber invariant has exactly one owner.

### Positive Consequences

* `sequenceNumber` invariant testable in isolation
* Entry construction logic not duplicated across three services
* Pattern is reusable: any future subclass written from multiple services follows the same approach

### Negative Consequences / Tradeoffs

* Additional CDI bean per ledger subclass with multiple writers
* Services are coupled to the writer rather than to the repository directly

## Pros and Cons of the Options

### Option A — Each service manages its own writes

* ✅ No additional abstraction
* ❌ No single owner for the sequenceNumber invariant
* ❌ Entry construction logic duplicated across services
* ❌ No isolation point for unit testing the invariant

### Option B — Dedicated writer bean (chosen)

* ✅ Single owner for sequenceNumber computation
* ✅ Unit-testable in isolation with Mockito
* ✅ Reusable pattern for other multi-service ledger subclasses
* ❌ One extra CDI bean per subclass-with-multiple-writers

### Option C — Separate subclasses per entry type

* ✅ Schema precisely matches semantics
* ❌ Two entity classes, two join tables, two migrations for sparse field differences
* ❌ Does not solve the sequenceNumber ownership problem

## Links

* `io.casehub.clinical.service.DeviationLedgerWriter` — reference implementation
* casehubio/parent#30 — platform protocol for this pattern (pending)
* casehubio/clinical#14 — issue that introduced this pattern
