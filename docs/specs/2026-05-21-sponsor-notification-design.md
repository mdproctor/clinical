# Sponsor Notification — Design Spec
**Issue:** casehubio/clinical#13  
**Branch:** issue-13-sponsor-notification  
**Date:** 2026-05-21

---

## Context

ICH E6(R3) §4.5 requires the investigator to notify the sponsor promptly of any protocol deviation that significantly affects subject safety or data integrity. MAJOR deviations carry `escalationRequirement = SPONSOR_NOTIFICATION`. The PI authorisation lifecycle (Epic 5) satisfies the PI accountability requirement; this issue satisfies the sponsor notification requirement.

`ProtocolDeviationResolvedEvent` is fired whenever a deviation reaches a terminal PI state. The `SPONSOR_NOTIFICATION` path currently has no consumer. This spec covers implementing that consumer.

---

## Design Decisions

| Question | Decision | Rationale |
|---|---|---|
| Notification content | Structured summary only — deviation type, severity, site, PI actor ID, terminal status, channel reference | GCP §4.5 requires prompt notification, not embedded CAPA. CAPA is a separate tracked obligation (clinical#21+). Free-text from qhorus DONE message is not a compliance artifact. |
| Terminal statuses that notify | All three: ESCALATED, REJECTED, EXPIRED | GCP obligation triggered by deviation significance, not PI cooperation. REJECTED and EXPIRED are each independently concerning to the sponsor. |
| Sponsor config location | Per-trial: `sponsorNotificationConnectorId` + `sponsorNotificationDestination` on `ClinicalTrial` | Sponsor contact is domain data, not deployment config. Multi-trial deployments require independent routing. |
| Event enrichment | Add `deviationType` (String) and `piId` (String, nullable) to `ProtocolDeviationResolvedEvent` | Both are known at fire time. Self-describing events decouple observers from persistence. `piId` cannot be cleanly recovered from DB post-fire without a ledger query. |
| Architecture | `SponsorNotifier` SPI with `DefaultSponsorNotifier` as `@DefaultBean` | Separates "decided to notify" (listener — domain) from "what notification means" (delivery + persistence + retry). `DurableSponsorNotifier` (clinical#21) slots in without touching the listener. |

---

## Components

### api/ (JPA-free)

**`SponsorNotifier`** — SPI interface:
```java
public interface SponsorNotifier {
    void notify(SponsorNotificationRequest request);
}
```

**`SponsorNotificationRequest`** — value object carrying all context the SPI needs:
```java
public record SponsorNotificationRequest(
    UUID deviationId,
    UUID siteId,
    UUID trialId,
    String deviationType,
    DeviationSeverity severity,
    PiApprovalStatus terminalStatus,
    String piId,                           // null for EXPIRED
    String sponsorNotificationConnectorId,
    String sponsorNotificationDestination
) {}
```

`EscalationRequirement` is omitted — by the time the SPI is called, the listener has already confirmed `SPONSOR_NOTIFICATION`. The SPI never re-filters.

**`ProtocolDeviationResolvedEvent`** — enriched with two fields:
```java
public record ProtocolDeviationResolvedEvent(
    UUID deviationId,
    UUID siteId,
    DeviationSeverity severity,
    EscalationRequirement escalationRequirement,
    PiApprovalStatus terminalStatus,
    String deviationType,    // always non-null
    String piId              // null for EXPIRED
) {}
```
Both `PiResponseListener` and `DeviationExpirer` update their fire-site to pass the new fields. `deviationType` comes from `ProtocolDeviation.deviationType`; `piId` comes from `senderId` in `PiResponseListener`, null in `DeviationExpirer`.

### runtime/

**`SponsorNotificationListener`** — thin CDI observer:
- Observes `@ObservesAsync ProtocolDeviationResolvedEvent`
- Filters: `escalationRequirement != SPONSOR_NOTIFICATION` → return
- Looks up `TrialSite` by `siteId` → gets `trialId`
- Looks up `ClinicalTrial` by `trialId` → gets connector config
- If either config field is null/blank → log warning, return (no SPI call, no ledger entry — this is a deployment gap, not a delivery attempt)
- Builds `SponsorNotificationRequest` with resolved config and fires `sponsorNotifier.notify()`

**`DefaultSponsorNotifier`** — `@ApplicationScoped @DefaultBean`:
- Resolves `Connector` by id from `@All Instance<Connector>` (casehub-connectors-core)
- If no connector found → log error, write failed ledger entry, return
- Calls `connector.send(ConnectorMessage)` with title + body (see message format below)
- On success → writes `"sponsor-notifier"` ledger entry with `delivered = true`
- On exception → logs error, writes `"sponsor-notifier-failed"` ledger entry with `delivered = false`; does not rethrow

Message format per terminal status:
- **ESCALATED**: `[MAJOR Deviation] PI {piId} approved — corrective action committed. Site: {siteId}. Type: {deviationType}. Ref: clinical/deviation/{deviationId}/pi-oversight`
- **REJECTED**: `[MAJOR Deviation] PI {piId} refused to authorise — no corrective action. Site: {siteId}. Type: {deviationType}.`
- **EXPIRED**: `[MAJOR Deviation] PI response deadline expired — no response received. Site: {siteId}. Type: {deviationType}.`

**`DeviationLedgerWriter`** — new method:
```java
public void writeSponsorNotifiedEntry(UUID deviationId, Instant notifiedAt, boolean delivered)
```
Fetches `ProtocolDeviation` internally. Sets `actorId = SYSTEM_ACTOR`, `actorRole = "sponsor-notifier"` (delivered) or `"sponsor-notifier-failed"` (not delivered). Populates `sponsorNotifiedAt` only when `delivered = true`. Sequence number is `resolution_entry_sequence + 1` — the invariant is preserved; `DeviationLedgerWriter` remains the sole writer.

---

## Data Model

### `ClinicalTrial` — two nullable columns

```sql
-- V108__sponsor_notification_config.sql (default datasource)
ALTER TABLE clinical_trial ADD COLUMN sponsor_notification_connector_id VARCHAR(64);
ALTER TABLE clinical_trial ADD COLUMN sponsor_notification_destination  VARCHAR(512);
```

### `ProtocolDeviationLedgerEntry` — one nullable column

```sql
-- V1008__sponsor_notified_at.sql (qhorus datasource)
ALTER TABLE protocol_deviation_ledger_entry ADD COLUMN sponsor_notified_at TIMESTAMP;
```

---

## Error Handling

| Failure mode | Where caught | Action |
|---|---|---|
| Trial has no connector config | `SponsorNotificationListener` | Log warning, return — no SPI call, no ledger entry |
| Connector ID not registered | `DefaultSponsorNotifier` | Log error, write `"sponsor-notifier-failed"` ledger entry |
| `Connector.send()` throws | `DefaultSponsorNotifier` try/catch | Log error with exception, write `"sponsor-notifier-failed"` ledger entry |

The PI resolution ledger entry is always committed before the CDI event fires. Notification failure never rolls back or obscures the resolution record. `writeSponsorNotifiedEntry` runs in its own `@Transactional` boundary.

---

## Testing

### Unit tests

**`SponsorNotificationListenerTest`** (`@InjectMock SponsorNotifier`):
- `SPONSOR_NOTIFICATION` + ESCALATED/REJECTED/EXPIRED → `notify()` called with correct request
- `NONE` / `IRB_REVIEW` → SPI not called
- Null trial or missing connector config → SPI not called, no exception

**`DefaultSponsorNotifierTest`** (`@InjectMock` connector + `DeviationLedgerWriter`):
- Matching connector → `send()` called, `writeEntry(delivered=true)`
- No connector for id → `send()` not called, `writeEntry(delivered=false)`
- `send()` throws → `writeEntry(delivered=false)`, exception not propagated
- Message body correct for each terminal status

**`DeviationLedgerWriterTest`** (extend existing):
- `delivered=true` → `sponsorNotifiedAt` populated, `actorRole = "sponsor-notifier"`, `sequenceNumber = N+1`
- `delivered=false` → `sponsorNotifiedAt` null, `actorRole = "sponsor-notifier-failed"`

### Integration tests (`@QuarkusTest`)

**`SponsorNotificationIntegrationTest`** — stub `SponsorNotifier` via `@TestProfile` alternative. POST deviation → PI DONE response → asserts `SponsorNotifier.notify()` called with correct `deviationId`, `deviationType`, `piId`, `terminalStatus`.

**`DefaultSponsorNotifierConnectorTest`** — WireMock for Slack webhook. Trial configured with `connectorId=slack` + WireMock destination. Verifies HTTP request body. WireMock stub reset between tests (per GE-20260501-29e3b8).

### Correctness tests

- Sequence number: sponsor notification entry is always `resolution_entry.sequenceNumber + 1`
- `sponsorNotifiedAt` within 1s of delivery (clock-injected)
- EXPIRED path: `piId` is null in request, message body omits PI reference

---

## Deferred

| Issue | What |
|---|---|
| clinical#21 | `DurableSponsorNotifier` — `SponsorNotification` entity, retry semantics, own ledger subtype |
| clinical#22 | `PATCH /trials/{id}/sponsor-config` — update sponsor config after trial creation |
| clinical#23 | PI actor ID → display name resolution via `PiDirectoryService` SPI |

---

## Platform doc update

After implementation: add `casehub-connectors-core` → `casehub-clinical/runtime` row to PLATFORM.md cross-repo dependency table.
