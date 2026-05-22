# casehub-clinical Agentic Harness — Layer Log

Structured record of what was built at each layer, optimised for LLM consumption and future tutorial generation. Correlates with blog entries in the workspace `blog/`, git history, and GitHub issues.

Each entry documents one layer of the adoption sequence — ordered for learning, not for chronology. Entries are written when work on a layer begins; sections marked `🔲` are placeholders with context for future sessions to fill in.

Cross-references:
- Blog entries: workspace `blog/` (staged; published to mdproctor.github.io via `publish-blog`)
- Design specs: workspace `specs/` and promoted to project `docs/specs/`
- Tutorial teaching objectives: `../parent/docs/tutorial-strategy.md §7`
- AML reference implementation: `../aml/LAYER-LOG.md` (Layers 1 and 2 complete)
- Platform compliance gap analysis: `docs/use-case-analysis.md §8.1` in casehub-parent

**Architectural note — no NaiveXxxService pattern:** casehub-clinical uses Panache Active Record entities as domain objects directly (documented exception in CLAUDE.md: no downstream consumers, application tier only). There is no `NaiveClinicalService.java` with `@DefaultBean` displacement as in AML. The "naive" layer IS the entities + REST API with no CaseHub foundation modules wired. This divergence is intentional — document it in each layer entry rather than treating it as a gap.

---

## Layer 1 — Naive Java (no CaseHub)

**Completed:** 2026-05-08 (Epic 1: scaffold `[8f628a8]`; Epic 2: domain model `[488ea67]`)
**Issues:** casehubio/clinical#1 (Epic 1), casehubio/clinical#2 (Epic 2)
**Blog:** `blog/2026-05-08-mdp01-clinical-foundation.md` — module split decision, FHIR validation, Quarkus surprises
**Key files:**
- `api/src/main/java/io/casehub/clinical/api/ClinicalCapabilities.java` — 8 capability tag string constants for the engine
- `api/src/main/java/io/casehub/clinical/api/ClinicalTrustDimensions.java` — 3 trust dimension constants (`safety-accuracy`, `eligibility-precision`, `protocol-adherence`)
- `api/src/main/java/io/casehub/clinical/api/model/CtcaeGrade.java` — Grade 1–5 with CTCAE v5.0 SLA durations; Grade 3/4 = 24h, Grade 5 = 1h (internal), Grade 1/2 = 7d
- `api/src/main/java/io/casehub/clinical/api/model/` — `TrialPhase`, `EnrollmentStatus`, `ConsentStatus`, `DeviationSeverity`, `PiApprovalStatus`, `IrbDecision`, `AeOutcome`, `EventActuality`
- `runtime/src/main/java/io/casehub/clinical/entity/ClinicalTrial.java` — trial: protocolId, phase, sponsor, targetEnrollment, status
- `runtime/src/main/java/io/casehub/clinical/entity/TrialSite.java` — investigator site: trialId, investigatorId, status
- `runtime/src/main/java/io/casehub/clinical/entity/PatientEnrollment.java` — per-patient: siteId, patientId, consentStatus, enrollmentStatus
- `runtime/src/main/java/io/casehub/clinical/entity/AdverseEvent.java` — safety event: enrollmentId, grade, occurredAt, reportedAt, slaDeadline, workItemId
- `runtime/src/main/java/io/casehub/clinical/entity/ProtocolDeviation.java` — deviation: type, severity, piApprovalStatus, reportedAt
- `runtime/src/main/java/io/casehub/clinical/entity/IrbApproval.java` — ethics gate: reviewType, committeeId, decisionDeadline, decision
- `runtime/src/main/java/io/casehub/clinical/resource/TrialResource.java` — POST/GET `/trials` and `/trials/{id}`
- `runtime/src/main/java/io/casehub/clinical/resource/SiteResource.java` — POST/GET `/trials/{id}/sites` and `/trials/{id}/sites/{id}`
- `runtime/src/main/java/io/casehub/clinical/resource/PatientResource.java` — POST/GET patients; POST adverse events (delegates to service in Layer 2)
- `runtime/src/test/java/io/casehub/clinical/resource/ShowcaseScenarioTest.java` — 3-site oncology scenario: register trial, 3 independent sites, enroll patients, verify ownership chain

### What it shows

The clinical domain model with no CaseHub foundation modules: six JPA entities, all domain enums sourced from FHIR R5 and CTCAE v5.0, capability tag constants, and a REST API that persists entities directly with no accountability, SLA, or audit wiring. Three REST resources expose the trial, site, and patient enrollment lifecycle. The 3-site showcase test registers a trial, adds independent sites, and enrolls patients.

This is the baseline every subsequent layer improves. The gaps are structural — the REST API for enrollment calls `enrollment.persist()` directly. No record exists of who enrolled a patient or when. No SLA governs how long an adverse event review can sit. No formal obligation exists when a deviation occurs. These absences are the teaching mechanism for Layers 2–5.

### The gap comments

Clinical does not use the `NaiveXxxService @DefaultBean` pattern (see architectural note above). The Layer 1 code has no explicit gap comments. The gaps visible in Layer 1 are architectural, not annotated:

- `PatientEnrollment.persist()` called directly — no attribution, no audit record. Who enrolled this patient? No record.
- `AdverseEvent` entity exists with no SLA enforcement — a safety officer can sit on a Grade 3 event indefinitely. GCP ICH E6(R3) §5.17 requires reporting within 24 hours. Nothing enforces this.
- `ProtocolDeviation` records that a deviation occurred — no named PI is required to take formal responsibility. A log entry proves the deviation was noticed, not that anyone was accountable for it.
- `IrbApproval` holds a `decisionDeadline` field — nothing fires if the deadline passes.
- No tamper-evident audit trail exists anywhere in Layer 1. ClinicalAgent has the same gap. The Merkle chain comes in Layer 4.

These gaps map directly to the compliance gap table in `docs/use-case-analysis.md §8.1` in casehub-parent.

### Key wiring

**Module split with Active Record exception.** Platform protocol requires `api/` (pure Java, no JPA) + `runtime/` (Quarkus app, entities, migrations). casehub-clinical applies the exception: no downstream consumers exist, so Panache Active Record entities in `runtime/` serve directly as domain objects. `api/` holds only enums and constants. This is documented in CLAUDE.md; downstream JPA complexity does not apply. Contrast with AML where `api/` also holds service interfaces.

**FHIR R5 validation of domain model.** Domain fields were validated against FHIR R5 `ResearchStudy`, `ResearchSubject`, and `AdverseEvent` before writing entities. Three gaps surfaced: `targetEnrollment` was missing (needed to know when `enrollment-complete` goal is met), `EnrollmentStatus` needed a full state machine (not just `ConsentStatus`), and `AdverseEvent` needed two timing fields (`occurredAt` vs `reportedAt` — GCP tracks both). The entity set reflects FHIR R5 field names and semantics.

**`CtcaeGrade` SLA sourcing.** Grades 3 and 4 trigger 24-hour expedited reporting per ICH E6(R3) §5.17. Grade 5 uses a 1-hour internal SLA — product policy stricter than GCP minimum; the Javadoc says so explicitly. Grades 1 and 2 carried `null` SLA initially (wrong — fixed in Epic 4 before Layer 2 shipped). The SLA values are defined in the enum and used throughout Layers 2 and 4.

**`quarkus-hibernate-validator` is not included by default.** `quarkus-rest` does not pull in Bean Validation. `@NotBlank`, `@NotNull`, `@Valid` compile and wire silently but do nothing without `quarkus-hibernate-validator`. A missing required field returns 200. Adding the dependency is required.

**Greenfield multi-module compile ordering.** `mvn compile -pl api,runtime` fails on a fresh checkout with `NoSuchFileException` if `api/` has no compiled classes yet. Quarkus `generate-code` scans `api/target/classes` before the Java compiler runs. Fix: `mvn install -pl api` first, then proceed. Resolves once `api/` has real sources.

### Gotchas

- **Symptom:** `@NotBlank`, `@NotNull`, `@Valid` annotations compile and wire but a missing required field returns 200.
  **Cause:** `quarkus-rest` does not include Bean Validation. The validation infrastructure is absent, not misconfigured.
  **Fix:** Add `quarkus.hibernate.validator` (or `quarkus-hibernate-validator`) to `runtime/pom.xml`.

- **Symptom:** `mvn compile -pl api,runtime` fails with `NoSuchFileException` pointing at `api/target/classes` on a fresh clone or after `mvn clean`.
  **Cause:** Quarkus `generate-code` goal on `runtime` scans `api/target/classes` before javac runs. Empty target directory causes failure.
  **Fix:** Two-phase build: `mvn install -pl api`, then `mvn compile -pl runtime`. Self-resolves once `api/` has compiled output in the local Maven repo.

- **Symptom:** Ownership check on GET patient returns 200 for patients belonging to a different trial when the caller knows a valid siteId.
  **Cause:** Initial GET handler validated `enrollment.siteId == siteId` but did not validate `site.trialId == trialId`. Ownership chain was asymmetric.
  **Fix:** Add `TrialSite.findById(siteId)` + `site.trialId.equals(trialId)` check to every GET that traverses the ownership hierarchy. Commit `328d449`.

### Pattern to replicate (in another domain)

1. Create `api/` Maven module — pure Java, zero framework imports, zero JPA. Holds domain enums and capability/trust constants only (clinical variant — AML also puts service interfaces here).
2. Source domain enums from authoritative standards before writing code. For clinical: FHIR R5 field names, CTCAE v5.0 grade definitions, ICH E6(R3) SLA obligations. The standard is the spec.
3. Define `CtcaeGrade` (or equivalent) with SLA durations embedded as `Duration` values — these anchor every SLA computation in Layers 2 and 4.
4. Create `runtime/` Maven module with Quarkus and Panache Active Record. Domain entities go here directly if no downstream JPA consumers exist.
5. Write Flyway migrations in `V100–V999` range if casehub-work will be added later — it occupies V1–V21+ and Quarkus scans transitive JARs.
6. Implement REST resources calling Panache directly (`Entity.persist()`) — no service layer yet. This is the Layer 1 state.
7. Add `quarkus-hibernate-validator` immediately — not optional.
8. Write a showcase test that exercises the full domain hierarchy end-to-end (trial → site → patient → adverse event). This test grows with each layer.
9. Validate the ownership chain in every GET handler: each path segment must be validated against its parent, not just its own existence.

---

## Layer 2 — + casehub-work (adverse event SLA enforcement)

**Completed:** 2026-05-12 (Epic 4: `[2696f98]`, `[b6ea707]`, `[15e274b]`)
**Note:** Layer 2 (casehub-work) and Layer 4 (casehub-ledger) were built simultaneously in Epic 4. They are separate tutorial entries in teaching order; both reference Epic 4 as the source.
**Issue:** casehubio/clinical#4
**Blog:** `blog/2026-05-12-mdp02-adverse-event-sla-wiring.md` — Flyway collision, escalation as deployment config, ledger field discovery
**Design spec:** workspace `specs/2026-05-11-epic4-adverse-event-escalation-design.md`
**Key files:**
- `runtime/src/main/java/io/casehub/clinical/service/AdverseEventService.java` — creates WorkItem with grade-keyed `claimDeadline`; writes ledger entry; persists entity — one `@Transactional` call
- `runtime/src/main/resources/db/migration/V106__adverse_event_work_item_id.sql` — adds `work_item_id UUID` to `adverse_event` table

### What it shows

Adds `casehub-work` to create a formal safety officer `WorkItem` with a grade-keyed `claimDeadline` — the GCP ICH E6(R3) adverse event reporting SLA. Grade 3/4 = 24 hours. Grade 5 (death) = 1 hour. Grade 1/2 = 7 days. The `AdverseEvent` entity now carries a `workItemId` — the caller can track the safety review independently of the event.

This closes the most visible gap in Layer 1: a Grade 3 adverse event could sit indefinitely. Now the platform escalates if the SLA deadline passes. ClinicalAgent has no equivalent — this single wiring delivers more structural regulatory value than ClinicalAgent's entire codebase.

### The gap comments

Layer 1 has no explicit gap comments (see architectural note). The Layer 1 absence that Layer 2 closes:

```
// Layer 1: adverse event persisted directly — no SLA, no formal obligation
// POST /adverse-events → ae.persist() → 201

// Layer 2: adverse event routed through AdverseEventService
// → WorkItem with claimDeadline = reportedAt + grade.sla()
// → Platform escalates on miss; clinical code does not change between deployments
```

`reportedAt` is set server-side — the client-supplied value is ignored. This anchors the SLA clock to the server's notion of when the event was documented, not the client's.

### Key wiring

**Flyway version collision — clinical domain migrations must start at V100.** casehub-work ships Flyway migrations in its JAR at `classpath:db/migration`, V1 through V21+. Quarkus Flyway scans transitive JARs for that path — not just application resources. Clinical's initial domain migrations were V1–V6 (wrong) and renamed to V100–V105 before Epic 4 landed. Any harness adding casehub-work must start its domain migrations at V100 or higher. This is documented in CLAUDE.md.

**`casehub-work-api` safe in `api/`, `casehub-work` (JPA runtime) only in `runtime/`.** Same pattern as AML Layer 2. `casehub-work-api` contains only request/response types (no JPA) — safe to add to the pure Java module. `casehub-work` with JPA entities goes in `runtime/pom.xml` only.

**`WorkItemCreateRequest` — key fields.** No builder yet (tracked in casehubio/work#168). Pass `null` for unused fields. Key fields for adverse event routing:
- `claimDeadline` = `ae.reportedAt + ae.grade.sla().orElseThrow()`
- `category` = `"adverse-event"`
- `candidateGroups` = `"safety-officers"` for Grade 1/2; `"dsmb,safety-officers"` for Grade ≥ 3
- `callerRef` = `"clinical:adverse-event/" + ae.id`

**Escalation policy is deployment configuration, not clinical code.** What happens when `claimDeadline` passes is wired via `EscalationPolicy` SPI at deployment time — spawn a DSMB WorkItem, fire a notification, etc. Clinical sets `candidateGroups` and `claimDeadline`. Different trial types or phases carry different escalation configs. casehub-connectors safety officer notification is deferred to casehubio/clinical#11.

**Hibernate scan packages — not applicable for this layer.** Clinical uses Panache Active Record (no explicit scan packages required) — unlike AML which required `io.casehub.work.runtime.model,io.casehub.work.runtime.filter`. This divergence is because clinical's Panache entities are in the same module as the runtime, not in a separate app module.

### Gotchas

- **Symptom:** Flyway startup fails with "Found more than one migration with version 1" after adding casehub-work.
  **Cause:** casehub-work ships `V1` through `V21+` at `classpath:db/migration`. Quarkus Flyway scans that path in all transitive JARs. If clinical also has a `V1` migration, both are found and Flyway refuses to start.
  **Fix:** Rename all clinical domain migrations to V100–V999 before adding casehub-work. Convention documented in CLAUDE.md. Applies to all casehub harnesses.

- **Symptom:** Flyway reports V1004 is already applied when trying to create `V1004__ae_ledger_entry.sql`.
  **Cause:** casehub-ledger added `V1004__actor_identity.sql` after the convention "V1004+ for consumer-owned ledger joins" was written. The convention predated the ledger's own use of V1004.
  **Fix:** Consumer-owned ledger subclass join tables start at V1005. `ae_ledger_entry` is at `V1005__ae_ledger_entry.sql`. CLAUDE.md updated. (This gotcha belongs to Layer 4 but surfaces at the same time as this layer since both were built in Epic 4.)

- **Symptom:** `AdverseEventService.reportAdverseEvent` throws "Failed to enlist. Check if a connection from another datasource is already enlisted to the same transaction" in tests.
  **Cause:** This method writes to two datasources (default for domain entity, qhorus for ledger entry) in one `@Transactional` call. Agroal's default local-transaction mode does not allow a second datasource to join an existing JTA transaction. H2 supports XA; Agroal just doesn't use it by default.
  **Fix:** Add to test `application.properties`: `quarkus.datasource.jdbc.transactions=xa` and `quarkus.datasource.qhorus.jdbc.transactions=xa`. (Also a Layer 4 concern — included here because it blocks this layer's tests.)

### Pattern to replicate (in another domain)

1. Rename all existing domain Flyway migrations to V100+ before adding casehub-work
2. Add `casehub-work-api` to `api/pom.xml`; add `casehub-work` to `runtime/pom.xml`
3. Implement a `@ApplicationScoped` service for the SLA-bearing domain event (adverse event, investigation, deviation):
   - Set `reportedAt` server-side — never trust the client's clock for SLA anchoring
   - Compute `slaDeadline` from the domain's severity/grade enum SLA duration
   - Create `WorkItemCreateRequest` with `claimDeadline = slaDeadline`, `candidateGroups` matching the domain's reviewer pool, `callerRef` as a URI for the domain entity
   - Persist the domain entity with `workItemId` set from the created WorkItem
4. Add XA transaction config to test `application.properties` if writing to two datasources in one `@Transactional` method
5. Test: unit-test the service (grade → claimDeadline arithmetic); `@QuarkusTest` asserting `workItemId` is set and `slaDeadline` is correct for each severity level; extend the showcase test with SLA assertions

---

## Layer 3 — + casehub-qhorus (PI authorisation for protocol deviations)

**Completed:** 2026-05-17 (Epic 5: `[55c90d5]`)
**Issue:** casehubio/clinical#5 (Epic 5: PI authorisation)
**Design spec:** workspace `specs/2026-05-15-epic5-pi-authorisation-design.md`
**Blog:** `blog/2026-05-15-mdp01-protocol-deviation-accountability.md` — design intent and classification problem
**Key files:**
- `api/src/main/java/io/casehub/clinical/api/spi/DeviationResponsePolicy.java` — SPI: deployers configure deadline + escalation per trial/site/phase/severity
- `api/src/main/java/io/casehub/clinical/api/spi/DeviationContext.java` — policy input: deviation identity, trial, site, phase, severity, type
- `api/src/main/java/io/casehub/clinical/api/spi/DeviationResponseRequirements.java` — policy output: piResponseDeadline (Duration) + escalationRequirement
- `api/src/main/java/io/casehub/clinical/api/model/EscalationRequirement.java` — NONE, SPONSOR_NOTIFICATION, IRB_REVIEW
- `api/src/main/java/io/casehub/clinical/api/ProtocolDeviationResolvedEvent.java` — CDI event fired on terminal state; consumed by Epic 6 (IRB) and Epic 13 (sponsor notification)
- `runtime/src/main/java/io/casehub/clinical/service/DefaultDeviationResponsePolicy.java` — @DefaultBean; reads deadlines from MicroProfile Config; MINOR→7d/NONE, MAJOR→72h/SPONSOR_NOTIFICATION, CRITICAL→24h/IRB_REVIEW
- `runtime/src/main/java/io/casehub/clinical/service/ClinicalInboundNormaliser.java` — InboundNormaliser SPI: maps `{"decision":"APPROVED"}` → DONE, `{"decision":"REJECTED"}` → DECLINE (scoped to /pi-oversight channels)
- `runtime/src/main/java/io/casehub/clinical/service/ProtocolDeviationService.java` — creates per-deviation channel, sends COMMAND, writes ledger entry, sets COMMANDED state
- `runtime/src/main/java/io/casehub/clinical/service/PiResponseListener.java` — process() called by tests; @ObservesAsync commented pending qhorus#153
- `runtime/src/main/java/io/casehub/clinical/service/DeviationExpirationJob.java` — @Scheduled hourly; marks overdue COMMANDED deviations EXPIRED
- `runtime/src/main/java/io/casehub/clinical/resource/DeviationResource.java` — POST + GET /trials/{t}/sites/{s}/deviations
- `runtime/src/main/resources/db/migration/default/V107__alter_protocol_deviation_add_commitment_fields.sql` — 4 new columns on protocol_deviation
- `runtime/src/main/resources/db/migration/qhorus/V1006__protocol_deviation_ledger_entry.sql` — join table for ProtocolDeviationLedgerEntry

### What it shows

Adds `casehub-qhorus` to issue a formal COMMAND to the named PI when a deviation is reported. The COMMAND creates a formal Commitment with a GCP-compliant deadline. The PI's structured JSON response (via `InboundNormaliser` SPI) updates the deviation status. Downstream epics consume `ProtocolDeviationResolvedEvent` without modifying this layer.

ClinicalAgent logs deviations. Logging proves notice. The COMMAND proves accountability — a named PI with a traceable obligation and a deadline the platform escalates if missed.

Layer 3 goes between Layers 2 and 4 in tutorial order even though it was built after Layer 4. The teaching sequence: SLA enforcement (Layer 2) → formal obligation (Layer 3) → tamper-evident audit (Layer 4).

### The gap comments

```
// Layer 1: ProtocolDeviation.persist() — records a deviation occurred,
// no named PI, no formal commitment, no deadline.
// In a GCP audit: proves notice, not accountability.

// Layer 3: ProtocolDeviationService.reportDeviation()
// → channel clinical/deviation/{id}/pi-oversight created (QUERY,COMMAND only)
// → COMMAND sent to site.investigatorId with correlationId = deviation.id
// → Commitment auto-opened by MessageService.send() (COMMAND type + correlationId)
// → piApprovalStatus = COMMANDED; responseDeadline = now + policy.evaluate(context).piResponseDeadline()
// → ProtocolDeviationLedgerEntry written (FDA audit trail)
```

### Key wiring

**Per-deviation channel, not per-site.** Channel naming: `clinical/deviation/{deviationId}/pi-oversight`. Per-site channels were considered but rejected: even though qhorus#154 now threads `correlationId` through `receiveHumanMessage()`, per-deviation channels remain the correct design — they make the deviation identity unambiguous regardless of whether the backend supplies a correlationId, and they scope `allowedTypes` correctly for the oversight semantic.

**`MessageService.send()` auto-opens Commitment on COMMAND type.** No explicit `commitmentService.open()` call needed in the service. The switch in `MessageService.send()`:
```java
case COMMAND -> commitmentService.open(UUID.randomUUID(), correlationId, channelId, ...)
```
The correlationId passed is `deviation.id.toString()` — this is the key for all future commitment operations (fulfill, decline, fail).

**`PiResponseListener` closes the Commitment explicitly (redundant after qhorus#154).** `PiResponseListener.process()` calls `commitmentService.fulfill(deviationId.toString())` for DONE and `commitmentService.decline(deviationId.toString())` for DECLINE. After qhorus#154 shipped, `ClinicalInboundNormaliser` passes `correlationId` through, so `MessageService.send()` now auto-fulfills/declines via the commitment state machine. The explicit call in `process()` is idempotent and redundant — it will be removed when casehubio/clinical#16 closes.

**`ClinicalInboundNormaliser` scoped to `/pi-oversight` channels.** The `InboundNormaliser` SPI is application-wide — all channels use the registered implementation. Scoping to oversight channel names prevents misclassifying messages on unrelated channels.

**`DeviationResponsePolicy` SPI with `@DefaultBean`.** Deployers override by providing an `@ApplicationScoped` bean without `@DefaultBean`. The default uses MicroProfile Config `Duration` properties:
```properties
casehub.clinical.deviation.minor.deadline=PT168H
casehub.clinical.deviation.major.deadline=PT72H
casehub.clinical.deviation.critical.deadline=PT24H
```

**`@ObservesAsync MessageReceivedEvent` commented out — blocked on qhorus#153.** The full CDI event chain (receiveHumanMessage → MessageReceivedEvent CDI event → PiResponseListener.process()) requires qhorus to fire a CDI event when an inbound message arrives. Until qhorus#153 ships, `process()` is called directly from tests. The integration test (`PiResponseListenerIntegrationTest`) is `@Disabled` with the qhorus issue reference.

**Flyway migration structure revised.** Adding both `casehub-qhorus` and `casehub-work` to the classpath causes a Flyway version collision: both ship migrations at `classpath:db/migration` with overlapping version numbers (V1–V9 and V1–V21). Fix: restructure clinical migrations into datasource-scoped subdirectories:
- `db/migration/default/` — clinical domain migrations (V100–V107); Flyway location set explicitly
- `db/migration/qhorus/` — clinical ledger join tables (V1005, V1006); Flyway scans this + qhorus jar
Tests use `drop-and-create` + Flyway disabled to avoid the classpath conflict.

**`LedgerEntry` field types differ from documentation.** `subjectId` is `UUID` (not `String`). `entryType` is `LedgerEntryType` enum (not `String`). `sequenceNumber` is `int` (not `long`). Find the reference usage in `LedgerPrivacyWiringIT.java` in casehub-ledger source — the only documented usage at time of writing.

### Gotchas

- **Symptom:** Flyway startup fails with "Found more than one migration with version 1" after adding both casehub-work and casehub-qhorus.
  **Cause:** Both JARs ship migrations at `classpath:db/migration`. Quarkus Flyway scans all JARs on the classpath for that location. V1–V9 (qhorus) collides with V1–V21 (casehub-work).
  **Fix:** Move all application migrations into datasource-scoped subdirectories (`db/migration/default/`, `db/migration/qhorus/`). Configure `quarkus.flyway.locations=classpath:db/migration/default` for the default datasource. Configure `quarkus.flyway.qhorus.locations=classpath:db/migration,classpath:db/migration/qhorus` for the qhorus datasource. Disable Flyway in tests (`quarkus.flyway.migrate-at-start=false`) and use `drop-and-create` instead. AML has the same latent issue — tracked casehubio/aml#20.

- **Symptom:** Application starts cleanly but startup logs show `ConfigValidationException` for `casehub.qhorus.reactive.enabled`.
  **Cause:** qhorus no longer exposes this config key in its model. The property was needed in an earlier qhorus version to suppress reactive extension activation.
  **Fix:** Remove `casehub.qhorus.reactive.enabled=false` from `application.properties`. Keep `quarkus.datasource.reactive=false` and `quarkus.datasource.qhorus.reactive=false` — those are standard Quarkus properties and still apply.

- **Symptom (historical — fixed by qhorus#154):** PI APPROVED/REJECTED response via `ChannelGateway.receiveHumanMessage()` did not close the Commitment — it remained OPEN.
  **Was:** `receiveHumanMessage()` called `messageService.send()` with `correlationId=null`. The auto-state-machine in `MessageService.send()` did nothing for DONE/DECLINE without a correlationId.
  **Now:** qhorus#154 added `correlationId` to `InboundHumanMessage` and `NormalisedMessage`. `ClinicalInboundNormaliser` passes it through. Auto-fulfillment fires. `PiResponseListener.process()` still calls `commitmentService.fulfill()` explicitly for safety (idempotent); that call will be removed in clinical#16.

- **Symptom:** `ClinicalInboundNormaliser` maps messages on non-PI channels to DONE or DECLINE if content happens to contain `"decision":"APPROVED"`.
  **Cause:** `InboundNormaliser` is a global singleton — every channel uses it.
  **Fix:** Scope the detection to channels whose name contains `/pi-oversight`. Any other channel defaults to QUERY.

### Pattern to replicate (in another domain)

1. Add `casehub-qhorus-api` to `api/pom.xml`; add `casehub-qhorus` to `runtime/pom.xml`
2. Restructure migrations into datasource subdirectories before the classpath collision manifests (see Gotchas)
3. Define a `DeviationResponsePolicy`-equivalent SPI in `api/spi/` — takes a context record with enough fields to scope deadline by domain attributes; returns deadline + downstream action
4. Implement `@DefaultBean` policy reading deadlines from MicroProfile Config with ISO-8601 Duration defaults
5. Create a per-entity oversight channel: `{domain}/{entity-type}/{id}/pi-oversight` with `allowedTypes=QUERY,COMMAND` and `ChannelSemantic.APPEND`
6. Send COMMAND via `MessageService.send()` with `correlationId = entity.id.toString()` and `target = responsibleParty` — auto-opens Commitment
7. Store channel name + commandedAt + responseDeadline + escalation requirement on the domain entity
8. Implement `InboundNormaliser` SPI scoped to oversight channels — map domain-specific response format to DONE/DECLINE/QUERY
9. Implement response listener with explicit `commitmentService.fulfill()` / `commitmentService.decline()` calls; qhorus#154 now threads correlationId through, so auto-fulfillment also fires — the explicit call is belt-and-suspenders until clinical#16 removes it
10. Implement expiration job: `@Scheduled @Transactional`, per-item try/catch, call `commitmentService.fail(entity.id.toString())` on each expired entity
11. Test: write response listener unit tests calling `process()` directly; write `@Disabled` integration test for the full qhorus#153 CDI chain

### Extended in clinical#14 — resolution entries complete the Merkle chain

**Issue:** casehubio/clinical#14 (clinical#15 fixes hardcoded `sequenceNumber`)
**Key files added:**
- `runtime/src/main/java/io/casehub/clinical/service/DeviationLedgerWriter.java` — centralised ledger writer; owns `sequenceNumber` computation via `findLatestBySubjectId`; provides `writeCommandEntry` and `writeResolutionEntry`; unit-testable with Mockito
- `runtime/src/main/resources/db/migration/qhorus/V1007__deviation_resolution_fields.sql` — adds `terminal_status VARCHAR(50)` and `resolved_at TIMESTAMP WITH TIME ZONE` to `protocol_deviation_ledger_entry`

The Layer 3 Merkle chain was initially incomplete: the COMMAND entry existed but no resolution entry was written when the PI responded or the deadline expired. An FDA inspector could see the PI was formally commanded but not how the obligation discharged. Clinical#14 closes this gap: `PiResponseListener.process()` and `DeviationExpirationJob.checkExpiredCommitments()` now write EVENT-type ledger entries with `terminalStatus` (APPROVED/REJECTED/ESCALATED/EXPIRED) and `resolvedAt`.

Three services write to the same ledger chain, so `sequenceNumber` ownership matters. `DeviationLedgerWriter` is the canonical pattern for this: one `@ApplicationScoped` bean owns sequence computation and entry construction for all write sites. See ADR-0002 in `docs/adr/`.

---

## Layer 4 — + casehub-ledger (FDA tamper-evident audit trail)

**Completed:** 2026-05-12 (Epic 4: `[a6e5055]` — built simultaneously with Layer 2)
**Note:** Layer 4 (casehub-ledger) and Layer 2 (casehub-work) were built simultaneously in Epic 4. They are separate tutorial entries in teaching order; the log entry for Layer 2 covers the Flyway and XA gotchas that also apply here.
**Issue:** casehubio/clinical#4
**Blog:** `blog/2026-05-12-mdp02-adverse-event-sla-wiring.md` — three casehub-ledger surprises
**Key files:**
- `runtime/src/main/java/io/casehub/clinical/ledger/AdverseEventLedgerEntry.java` — ledger subclass in the `ledger` package (not `entity`)
- `runtime/src/main/java/io/casehub/clinical/service/AdverseEventLedgerWriter.java` — centralised ledger writer; owns `sequenceNumber` via `findLatestBySubjectId`; provides `writeReportEntry`; unit-testable with Mockito
- `runtime/src/main/resources/db/migration/V1005__ae_ledger_entry.sql` — join table for `AdverseEventLedgerEntry`

### What it shows

Adds `casehub-ledger` to write a tamper-evident `AdverseEventLedgerEntry` into the Merkle audit chain when an adverse event is reported. The entry is written in the same `@Transactional` call as the WorkItem creation and entity persist. A ledger write failure rolls back everything — no partial state.

This closes the "no audit trail" gap from Layer 1: the complete decision chain for every adverse event at every site is now independently verifiable by the FDA. ClinicalAgent has no equivalent. The Merkle chain means no post-hoc modification is undetectable.

### The gap comments

The Layer 1 absence that Layer 4 closes: the adverse event was persisted with no tamper-evident record. Any database modification (intentional or accidental) is undetectable. The ledger entry provides independent verifiability.

```
// Layer 1: ae.persist() — no audit trail
// Any post-hoc modification is undetectable; FDA cannot independently verify the record

// Layer 4: AdverseEventLedgerEntry written to Merkle chain in same transaction
// Independently verifiable; modification of either record is detectable
```

### Key wiring

**`AdverseEventLedgerEntry` must be in a separate package from domain entities.** Panache entities cannot span two persistence units. `AdverseEventLedgerEntry` extends `LedgerEntry` (qhorus PU) — if it lives in `io.casehub.clinical.entity` (listed in both PUs), Quarkus fails the build immediately: "Panache entities do not support being attached to several persistence units." The fix: `io.casehub.clinical.ledger` package, listed only under the qhorus PU configuration.

```properties
# Default PU: clinical domain entities
quarkus.hibernate-orm.packages=io.casehub.clinical.entity
# qhorus PU: ledger subclasses only
quarkus.hibernate-orm.qhorus.packages=io.casehub.ledger.runtime.entity,io.casehub.clinical.ledger
```

**`beans.xml` `<alternatives>` is silently ignored by Quarkus ArC.** `JpaLedgerEntryRepository` is `@Alternative @ApplicationScoped`. Standard CDI wires it via `<alternatives>` in `beans.xml`. Quarkus ArC ignores `beans.xml` alternatives — the application starts cleanly with no warning, then CDI fails with "Unsatisfied dependency for type LedgerEntryRepository." The actual fix is an `application.properties` entry:

```properties
quarkus.arc.selected-alternatives=io.casehub.ledger.runtime.repository.jpa.JpaLedgerEntryRepository
```

This must be in both `application.properties` (runtime) and test `application.properties`.

**`LedgerEntry` fields — no builder, no documented list.** `LedgerEntryRepository.save()` expects `id`, `subjectId`, `sequenceNumber`, `entryType`, `actorId`, `actorType`, `actorRole`, and `occurredAt` — all set by the caller. No builder, no factory, no documented required fields list at the time of writing. The only usage example was a test helper method in `LedgerPrivacyWiringIT.java` (in casehub-ledger source). Found by reading source.

**V1004 is taken by casehub-ledger.** The convention "V1004+ for consumer-owned ledger subclass join tables" was written before casehub-ledger added `V1004__actor_identity.sql`. Consumer join tables start at V1005. `AdverseEventLedgerEntry` join table is at `V1005__ae_ledger_entry.sql`. CLAUDE.md updated.

**XA transactions required for dual-datasource writes in tests.** See Layer 2 Gotchas — applies here identically. `reportAdverseEvent` writes to two datasources in one `@Transactional` call. Test `application.properties` must include:

```properties
quarkus.datasource.jdbc.transactions=xa
quarkus.datasource.qhorus.jdbc.transactions=xa
```

### Gotchas

- **Symptom:** Quarkus build fails with "Panache entities do not support being attached to several persistence units" when `AdverseEventLedgerEntry` is in `io.casehub.clinical.entity`.
  **Cause:** The package is listed in both the default PU and the qhorus PU. Panache entities cannot be scanned by two PUs simultaneously.
  **Fix:** Move `AdverseEventLedgerEntry` to `io.casehub.clinical.ledger`. List that package only under the qhorus PU. Do not list it in the default PU packages.

- **Symptom:** Application starts cleanly but CDI fails at injection point with "Unsatisfied dependency for type LedgerEntryRepository" or "Ambiguous dependencies".
  **Cause:** `JpaLedgerEntryRepository` is `@Alternative @ApplicationScoped`. Quarkus ArC ignores `beans.xml` `<alternatives>` entirely — no warning is emitted. The alternative is not activated.
  **Fix:** Add `quarkus.arc.selected-alternatives=io.casehub.ledger.runtime.repository.jpa.JpaLedgerEntryRepository` to both `application.properties` and test `application.properties`. Do not rely on `beans.xml`.

- **Symptom:** LedgerEntry write succeeds but required fields (`sequenceNumber`, `actorId`, `actorType`) are null, causing downstream validation or Merkle computation to fail.
  **Cause:** No builder or documented required field list. Easy to miss non-obvious required fields without a reference implementation.
  **Fix:** Find `LedgerPrivacyWiringIT.java` in casehub-ledger source — the test helper method is the only documented usage example at time of writing. Copy the field list; do not rely on documentation.

### Pattern to replicate (in another domain)

1. Add `casehub-ledger` to `runtime/pom.xml`
2. Create a ledger subclass in a dedicated package (e.g. `io.casehub.{domain}.ledger`, not `.entity`):
   - Extends `LedgerEntry`
   - `@DiscriminatorValue("YOUR_EVENT_TYPE")`
   - Domain-specific fields: event id, subject id, severity/grade, timing fields
3. Add the ledger package only to the qhorus PU packages config — never to the default PU
4. Add `quarkus.arc.selected-alternatives=io.casehub.ledger.runtime.repository.jpa.JpaLedgerEntryRepository` to both `application.properties` files
5. Add XA transaction config to test properties (see Layer 2 pattern step 4)
6. Create Flyway join table migration at V1005+ (V1004 is taken by casehub-ledger itself)
7. Populate all required `LedgerEntry` fields before calling `save()` — reference `LedgerPrivacyWiringIT.java` in casehub-ledger for the required field list
8. Wrap domain entity persist, WorkItem creation, and ledger write in a single `@Transactional` method — atomic or nothing
9. Test: verify the `LedgerEntry` is written with correct fields; verify that a simulated ledger write failure rolls back the entity persist and WorkItem creation
10. When a ledger subclass is written from multiple services: extract a dedicated `@ApplicationScoped` writer bean that owns `sequenceNumber` computation and entry construction for all write sites. Each service injects the writer and calls named methods. The invariant is testable with a mocked repository in isolation. See `DeviationLedgerWriter` and ADR-0002 in `docs/adr/`.

### Extended in clinical#15 — AdverseEventLedgerWriter extracted

**Issue:** casehubio/clinical#15 (part of issue-24-minor-cleanups `[d771025]`)
**Key files added:**
- `runtime/src/main/java/io/casehub/clinical/service/AdverseEventLedgerWriter.java` — centralised ledger writer; owns `sequenceNumber` computation via `findLatestBySubjectId`; provides `writeReportEntry`; ready for resolution and escalation entries in later epics

The initial Layer 4 implementation wrote the ledger entry inline in `AdverseEventService`. `AdverseEventLedgerWriter` extracts that into a dedicated `@ApplicationScoped` bean, mirroring `DeviationLedgerWriter` from Layer 3 clinical#14. Motivation: Epic 6 (IRB gate) will write resolution and escalation entries to the same audit chain — without the extracted writer, `sequenceNumber` ownership would be split across multiple services with no single point of truth. The extraction is a preparation step, not a behaviour change: `writeReportEntry` is the only method; `writeResolutionEntry` and similar will be added as later epics land.
