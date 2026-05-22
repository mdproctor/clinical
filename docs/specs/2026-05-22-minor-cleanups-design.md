# Design: Minor Cleanups — #24, #25, #15
**Date:** 2026-05-22
**Branch:** issue-24-minor-cleanups
**Issues:** casehubio/clinical#24, #25, #15

---

## #24 — TestSlackConnector: static → instance state

### Problem

`TestSlackConnector` uses `public static final List<ConnectorMessage> sent` and
`public static volatile boolean shouldThrow`. Tests access these directly in
`@BeforeEach`. Fragile under parallel execution and inconsistent with the platform
protocol `quarkus-test-stateful-bean-isolation` (PP-20260508-5c0e4c), which requires
stateful test beans to expose a `reset()` method and be injected in test classes.

### Design

Convert `sent` and `shouldThrow` to instance fields. `CopyOnWriteArrayList` is
unnecessary (serial execution) — replace with `ArrayList`. Add `public void reset()`
clearing both fields.

`DefaultSponsorNotifierTest` and `SponsorNotificationIntegrationTest` inject
`@Inject TestSlackConnector slackConnector` and call `slackConnector.reset()` in
`@BeforeEach` instead of the static field access.

### Files changed

| Action | File |
|--------|------|
| Modify | `runtime/src/test/java/io/casehub/clinical/service/TestSlackConnector.java` |
| Modify | `runtime/src/test/java/io/casehub/clinical/service/DefaultSponsorNotifierTest.java` |
| Modify | `runtime/src/test/java/io/casehub/clinical/service/SponsorNotificationIntegrationTest.java` |

---

## #25 — Sponsor notification minor gaps

### Gap 1 — Misleading log for partial connector config

**Problem:** `SponsorNotificationListener` logs "has no sponsor notification config"
whether both fields are null or only one. An operator cannot tell if config is
absent or misconfigured.

**Fix:** Split the null check into two guards:
- `trial == null` → "no sponsor notification config"
- One or both of `connectorId`/`destination` null → "incomplete sponsor notification
  config (connectorId=X, destination=Y)"

### Gap 2 — Hardcoded severity in `buildTitle()`

**Problem:** `DefaultSponsorNotifier.buildTitle()` produces `"[MAJOR Deviation] ..."`
regardless of `req.severity()`. Silently wrong if `SPONSOR_NOTIFICATION` escalation
ever applies to non-MAJOR deviations.

**Fix:** Replace with `"[" + req.severity().name() + " Deviation] " + ...`

### Gap 3 — Missing partial-config test coverage

**Problem:** `SponsorNotificationListenerTest.missing_connector_config_does_not_call_spi`
only tests both-null case. Partial config (one field null) is untested.

**Fix:** Add two test cases:
- `partial_config_connectorId_null_does_not_call_spi`
- `partial_config_destination_null_does_not_call_spi`

Both verify `verifyNoInteractions(sponsorNotifier)`.

### Files changed

| Action | File |
|--------|------|
| Modify | `runtime/src/main/java/io/casehub/clinical/service/SponsorNotificationListener.java` |
| Modify | `runtime/src/main/java/io/casehub/clinical/service/DefaultSponsorNotifier.java` |
| Modify | `runtime/src/test/java/io/casehub/clinical/service/SponsorNotificationListenerTest.java` |

---

## #15 — AdverseEventLedgerWriter extraction

### Problem

`AdverseEventService.writeLedgerEntry()` hardcodes `entry.sequenceNumber = 1`.
When Epic 4 adds resolution and escalation ledger entries for adverse events, sequence
numbers will duplicate. The correct pattern — `findLatestBySubjectId().map(e ->
e.sequenceNumber + 1).orElse(1)` — already exists in `DeviationLedgerWriter`.

Additionally, inline ledger writes in a service class are an anti-pattern: Epic 4 will
need resolution entries, forcing surgery on `AdverseEventService` rather than extension
of a dedicated writer.

### Design

Extract `AdverseEventLedgerWriter` (`@ApplicationScoped`), mirroring `DeviationLedgerWriter`:

- Injects `LedgerEntryRepository` and `Clock`
- `writeReportEntry(AdverseEvent ae)` — creates `AdverseEventLedgerEntry` with correct
  `nextSequenceNumber`, `clock.instant()` for `occurredAt`, all AE domain fields.
  Persists via `ledgerEntryRepository.save()`
- `private int nextSequenceNumber(UUID aeId)` — `findLatestBySubjectId(aeId)
  .map(e -> e.sequenceNumber + 1).orElse(1)`

`AdverseEventService` drops `writeLedgerEntry()` entirely, injects
`AdverseEventLedgerWriter`, and calls `adverseEventLedgerWriter.writeReportEntry(ae)`.
`AdverseEventService` retains `Instant.now()` for setting `ae.reportedAt` (entity
field). The writer uses `clock.instant()` for the ledger `occurredAt`.

### GE notes

GE-20260429-2e1c4f documents that `MAX()+1` has a race window and that the
`findLatestBySubjectId` pattern (ORDER BY DESC LIMIT 1) is the correct approach.
Concurrent adverse events for a single subject are clinically implausible, but we
follow the established pattern for correctness.

### Tests

`AdverseEventLedgerWriterTest` (`@QuarkusTest`, `@InjectMock LedgerEntryRepository`):
- `writeReportEntry_sequenceNumber1WhenNoPriorEntries` — verifies seq=1 when no prior
- `writeReportEntry_sequenceNumberIncrementsFromPrior` — verifies seq=N+1

Existing tests in `AdverseEventServiceTest` that assert `sequenceNumber=1` remain
valid (only one entry per AE today). A comment marks them for update in Epic 4 when
resolution entries are added.

### Files changed

| Action | File |
|--------|------|
| Create | `runtime/src/main/java/io/casehub/clinical/service/AdverseEventLedgerWriter.java` |
| Modify | `runtime/src/main/java/io/casehub/clinical/service/AdverseEventService.java` |
| Create | `runtime/src/test/java/io/casehub/clinical/service/AdverseEventLedgerWriterTest.java` |

---

## Out of scope

- `AdverseEventService` using `Clock` injection for `ae.reportedAt` — orthogonal to
  this branch; file as a separate issue if desired
- Parallel test execution enablement — #24 removes the blocker but does not enable it
- `DurableSponsorNotifier` (#21), PI display name resolution (#23) — separate issues
