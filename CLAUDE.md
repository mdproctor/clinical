# CLAUDE.md

**Name:** casehub-clinical

## Project Type

**Type:** java

**Stack:** Java 21 (on Java 26 JVM), Quarkus 3.32.2, GraalVM 25 (native image target)

---

## Work Tracking

**Issue tracking:** enabled
**GitHub repo:** casehubio/clinical

**Automatic behaviours:**
- Before implementation begins — check for an active issue. If none, run issue-workflow Phase 1 before writing any code.
- Before any commit — confirm issue linkage.
- All commits reference an issue — `Refs #N` or `Closes #N`.
- All commits must also reference the parent epic — include the epic issue number in the commit message or PR description.

---

## Platform Docs
- [Platform Index](https://raw.githubusercontent.com/casehubio/parent/main/docs/INDEX.md) — discovery index (start here)
- [Building Platform](https://raw.githubusercontent.com/casehubio/parent/main/docs/guides/building-platform.md) — platform contributor guide

## Repo Guide

This repo owns its own documentation, synced to parent via CI:
- `docs/guides/consumer-guide.md` — for app builders: modules, APIs, quick start
- `docs/guides/contributor-guide.md` — for platform builders: architecture, SPIs, internals

Update the relevant guide in the same session when implementation changes modules, SPIs, or public APIs. Do not defer — drift compounds.

Read `docs/guides/consumer-guide.md` for app-level work. Only read `docs/guides/contributor-guide.md` when modifying this repo's internals or extension points.

## Platform Context

This repo is one component of the casehubio multi-repo platform. **Before implementing anything — any feature, SPI, data model, or abstraction — run the Platform Coherence Protocol.**

> **Platform docs:** Local paths use `../parent/docs/` as root. If a path doesn't exist, the parent repo isn't cloned locally — fetch from `https://raw.githubusercontent.com/casehubio/parent/main/docs/<path>` instead.

**Platform architecture (fetch before any implementation decision):**
```
../parent/docs/PLATFORM.md
```

**Foundation repo deep-dives:**
- casehub-engine: `../parent/docs/repos/casehub-engine.md`
- casehub-ledger: `../parent/docs/repos/casehub-ledger.md`
- casehub-work: `../parent/docs/repos/casehub-work.md`
- casehub-qhorus: `../parent/docs/repos/casehub-qhorus.md`
- casehub-connectors: `../parent/docs/repos/casehub-connectors.md`

---

## Agentic Harness Goals

**Read first:** `../parent/docs/AGENTIC-HARNESS-GUIDE.md`

**Goal:** Production-grade clinical trial coordination harness demonstrating that GCP, FDA, and GDPR requirements are structurally satisfied by CaseHub's accountability layer where workflow-based LLM systems cannot provide equivalent compliance guarantees.

**Architecture record:** `ARC42STORIES.MD` is the primary architecture record (bootstrapped 2026-06-02, casehubio/clinical#54). `LAYER-LOG.md` is the source-of-truth draft that feeds it. A layer is not complete until its entry is written in both. See `../parent/docs/arc42stories-spec.md` and `../parent/docs/arc42stories-casehub-profile.md`.

---

## What This Project Is

`casehub-clinical` is an **agentic harness for clinical trial coordination** built on the CaseHub platform foundation. It coordinates eligibility screening agents, safety monitoring agents, PI authorisation gates, and IRB approval gates — producing an FDA-compliant, GDPR-aware, independently verifiable audit trail.

This is an **application layer**, not a framework. The foundation provides coordination, accountability, audit, and compliance primitives. casehub-clinical provides the clinical trial domain logic: what a trial protocol is, how a site manages patient enrollment, how adverse events escalate, and how the FDA audit trail is constructed.

### Why Clinical Trials

Clinical trials operate under the strictest regulated AI requirements of any domain: GCP (ICH E6(R3)), FDA IND requirements, EMA CTR, and GDPR for patient data. Every agent decision must be traceable. Every protocol deviation must be authorised by a named Principal Investigator with a formal commitment. Every adverse event has a hard reporting deadline (24 hours for serious events, 7 days for others) with documented escalation.

CaseHub's accountability properties close compliance gaps that no amount of prompt engineering or pipeline orchestration can address:

| GCP / ICH / FDA requirement | Without CaseHub | With casehub-clinical |
|---|---|---|
| Adverse event SLA — serious within 24h, others within 7 days | No deadline tracking | WorkItem `claimDeadline` with auto-escalation |
| Protocol deviation authorisation — PI must formally approve | Agent decides autonomously; no named responsible party | COMMAND from PI required; commitment lifecycle tracks acknowledgement and resolution |
| Consent withdrawal cascade — GDPR Art.17 patient data erasure | No GDPR capability | `LedgerErasureService` + `DecisionContextSanitiser` SPI |
| Multi-site independence — 50+ sites with independent rollup to trial level | Single-site linear pipeline | Blackboard aggregation per trial with cross-site pattern detection |
| Tamper-evident audit — FDA audit trail independently verifiable | No audit trail | Merkle Mountain Range + Ed25519-signed checkpoints |
| Trust-weighted safety agent routing — reliable agents on high-risk decisions | No trust model | Bayesian Beta + EigenTrust via `TrustWeightedSelectionStrategy` |

Full gap analysis in `docs/use-case-analysis.md` in casehub-parent (§8.1).

---

## Layering Rule

This is an application, not a framework. If the capability requires knowledge of clinical trial protocols, GCP, FDA IND, or patient consent, it belongs here. If it is purely about cases, commitments, trust, or audit records, it belongs in the foundation.

---

## Reference Documents (in casehub-parent)

| Document | What it covers |
|----------|---------------|
| `../parent/docs/AGENTIC-HARNESS-GUIDE.md` | Goals, what to produce, retroactive work instructions, layer maintenance |
| `../parent/docs/repos/casehub-clinical.md` | Harness structure, layer status |
| `../parent/docs/use-case-analysis.md` | Use case scoring, clinical trial selection rationale (§8.1), GCP compliance gap analysis |
| `../garden/docs/protocols/casehub/HARNESS-INDEX.md` | CaseHub app protocols |
| `../garden/docs/protocols/universal/INDEX.md` | Universal Java/Quarkus protocols |

## External Reference Standards

Consult these before making domain model, compliance, or grading decisions:

| Standard / Reference | What it covers | Use for |
|----------------------|---------------|---------|
| [ICH E6(R3) GCP](https://www.ich.org/page/efficacy-guidelines) | Good Clinical Practice — authoritative source for trial conduct, adverse event reporting obligations, PI responsibilities | Compliance requirements, SLA derivation, PI authorisation obligations |
| [CTCAE v5.0](https://ctep.cancer.gov/protocoldevelopment/electronic_applications/ctc.htm) | NCI Common Terminology Criteria for Adverse Events — Grade 1-5 definitions and severity thresholds | `CtcaeGrade` enum, SLA assignments per grade |
| [21 CFR Part 312](https://www.ecfr.gov/current/title-21/chapter-I/subchapter-D/part-312) | FDA IND requirements — expedited safety reporting, protocol amendments, sponsor obligations | FDA reporting SLAs, audit trail requirements |
| [FHIR ResearchStudy / ResearchSubject](https://hl7.org/fhir/researchstudy.html) | HL7 FHIR standard data model for clinical trials and subjects | Domain model field names and relationships — canonical reference for what fields a trial/site/patient needs |
| [ClinicalAgent (arXiv 2404.14777)](https://arxiv.org/abs/2404.14777) | Peer-reviewed open-source baseline (ACM BCB '24) | Comparison baseline — what casehub-clinical must structurally exceed |
| [OpenStudyBuilder](https://github.com/NovoNordisk-OpenSource/openstudybuilder) | Open source CDISC-based clinical study management (Novo Nordisk) | Reference implementation for trial protocol and study design data models |

---

## Design Phase References

Read these **before designing**, not after. The concern column tells you when each applies.

### Domain model and API design

| Concern | Read first |
|---------|-----------|
| Designing a new entity, record, or SPI | `casehub-clinical.md` — does clinical already own this? `PLATFORM.md` capability ownership table — does the foundation own it? |
| Module placement (`api/` vs `runtime/`) | Active Record exception: clinical has no downstream JPA consumers — Panache entities live in `runtime/` only; `api/` holds enums and constants only. See LAYER-LOG.md architectural note. |
| FHIR field names and entity relationships | [FHIR ResearchStudy / ResearchSubject](https://hl7.org/fhir/researchstudy.html) — canonical field names |
| Adverse event grade SLAs | CTCAE v5.0 + ICH E6(R3) §5.17 — Grade 3/4 = 24h, Grade 5 = 1h (internal), Grade 1/2 = 7d |
| Compliance requirement for a new feature | `use-case-analysis.md §8.1` — GCP gap table; 21 CFR Part 312 for FDA IND |

### Layer design

| Concern | Read first |
|---------|-----------|
| Deciding which layer a feature belongs in | Foundation Layers section below |
| Documenting a completed layer | LAYER-LOG.md — write the entry before closing the issue |

### Foundation integration

| Concern | Read first |
|---------|-----------|
| casehub-work (WorkItem, SLA, escalation) | `../parent/docs/repos/casehub-work.md` |
| casehub-qhorus (COMMAND/RESPONSE/DONE/DECLINE, Commitment) | `../parent/docs/repos/casehub-qhorus.md` |
| casehub-ledger (Merkle audit, GDPR erasure, LedgerEntry subclasses) | `../parent/docs/repos/casehub-ledger.md`; LedgerEntry subclasses → `io.casehub.clinical.ledger` package |
| casehub-engine (CasePlanModel, bindings, sub-cases) | `../parent/docs/repos/casehub-engine.md` |
| Boundary check — foundation or clinical? | `PLATFORM.md` boundary rules; Layering Rule section in this file |

### Persistence and migrations

| Concern | Read first |
|---------|-----------|
| New Flyway migration | Clinical uses datasource-scoped dirs — see Flyway migration structure in Ecosystem Conventions |
| Migration version number | V100-V999 clinical domain (default datasource); V2000+ ledger subclass join tables (qhorus datasource) |
| LedgerEntry subclass | Must live in `io.casehub.clinical.ledger` — never in `io.casehub.clinical.entity`; Panache cannot span two PUs |
| LedgerEntry subclass `domainContentBytes()` | Every `LedgerEntry` subclass with persistent `@Column` fields MUST override `domainContentBytes()`. The `LedgerProcessor` build-time validator (casehub-ledger SNAPSHOT) enforces this — CDI deployment fails if missing. Return `String.join("|", field1, field2, ...).getBytes(StandardCharsets.UTF_8)`. See all 6 clinical subclasses for reference. |
| Cross-datasource `@Transactional` | Requires XA on both datasources — see Multi-datasource XA in Ecosystem Conventions |

### Testing

| Concern | Read first |
|---------|-----------|
| `@QuarkusTest` setup | Test `application.properties` — drop-and-create + Flyway disabled; XA transactions; reactive suppression |
| Single-class test run | `mvn test -pl runtime -Dtest=ClassName --batch-mode` — requires `api` installed first |
| Testing a writer bean (e.g. `AdverseEventLedgerWriter`) | Unit test with Mockito-mocked `LedgerEntryRepository`; verify sequence number logic without Quarkus |

---

## What casehub-clinical Must Build

### Domain Model

**Trial entities:**
- `ClinicalTrial` — the trial: `{protocolId, phase, sponsor, sites[], status, trialLevelStatus}`
- `TrialSite` — one investigator site: `{siteId, investigatorId, patients[], status}`
- `PatientEnrollment` — per-patient: `{patientId, eligibilityCriteria[], consentStatus, safetyEvents[]}`
- `ProtocolDeviation` — recorded deviation: `{deviationType, severity, piApprovalStatus, reportedAt}`
- `AdverseEvent` — safety event: `{severity, reportedAt, slaDeadline, escalationStatus}`
- `IrbApproval` — IRB/ethics gate: `{reviewType, committeeId, decisionDeadline, decision}`

**Capability tags:**
- `eligibility-screening` — assess patient against protocol inclusion/exclusion criteria
- `safety-monitoring` — detect and classify adverse events by severity
- `protocol-review` — assess proposed protocol deviations
- `irb-consultation` — IRB/ethics committee WorkItem gate
- `pi-authorisation` — Principal Investigator COMMAND (protocol deviations require PI commitment)
- `data-safety-monitoring` — DSMB-level safety review (independent of site)
- `regulatory-submission` — FDA/EMA submission preparation and traceability
- `trial-supervisor` — LLM supervisor: protocol amendment analysis, cross-site pattern detection

**Trust dimensions:**
- `safety-accuracy` — adverse event classification accuracy vs subsequent safety outcomes
- `eligibility-precision` — false positive rate on eligibility screening (patients excluded who should have enrolled)
- `protocol-adherence` — track record of flagging deviations vs missing them

### Trial Coordination CasePlanModel

Goals:
- `enrollment-complete` — target patient count reached across all sites
- `safety-monitoring-active` — all enrolled patients have active safety monitoring
- `regulatory-compliant` — all required reporting obligations met within SLA

Key bindings (site-level sub-case):
- `eligibility-screening` fires on new patient registration
- `adverse-event-escalation` fires when safety-monitoring reports Grade >= 3 event — 24h WorkItem SLA
- `pi-authorisation-required` fires on protocol deviation — COMMAND to PI, creates formal Commitment
- `irb-consultation` fires when PI-authorised deviation requires ethics review — 72h WorkItem
- `dsmb-review` fires on accumulation of safety signals across sites — trial-level sub-case coordination

### Multi-Site Sub-Case Structure

```
Trial case (parent)
├── Site A sub-case
│   ├── Patient enrollment bindings
│   ├── Adverse event monitoring bindings
│   └── Protocol deviation bindings
├── Site B sub-case
│   └── ...
└── Trial-level rollup binding (aggregates site sub-cases)
    → DSMB review triggers when safety signal threshold crossed across >= 2 sites
```

Trial-level binding fires on aggregated context from all site sub-cases — no site-level agent reasons about this; the engine detects the cross-site pattern from accumulated blackboard state.

### Foundation Gates

| Capability | Foundation prerequisite |
|-----------|------------------------|
| Site-level sub-case orchestration | engine#112 CLOSED (2026-05-15) — SubCaseCompletionListener, SubCaseCompletionService wired; sub-case execution available in 0.2-SNAPSHOT |
| Adverse event SLA WorkItem | casehub-work production |
| PI authorisation commitment lifecycle | P0 complete (engine#186, qhorus) |
| GDPR consent withdrawal (Art.17) | LedgerErasureService |
| FDA Merkle audit trail | CaseLedgerEntry (2026-04-26) |
| EU AI Act Art.12 ComplianceSupplement | casehub-ledger |
| HITL WorkItem → case signal (IRB gate) | work#136 closed; `WorkItemLifecycleAdapter` in engine/work-adapter — IRB gate unblocked |
| Trust-weighted safety agent routing | P1.3 TrustWeightedSelectionStrategy wired in engine |
| LLM protocol amendment supervisor | AgentProvider SPI (platform-agent-api); `LlmProtocolAmendmentAdvisor` wired (clinical#86) |

### Foundation Layers

Each layer corresponds to a foundation module integration step. LAYER-LOG.md tracks completion — a layer is not done until its entry is written.

```
Layer 1: domain baseline — FHIR R5 domain model, six entities, REST CRUD, no accountability (Epics 1+2)
Layer 2: + casehub-work — adverse event SLA (GCP ICH E6(R3) §5.17; Grade 3/4 = 24h) (Epic 4)
Layer 3: + casehub-qhorus — PI authorisation COMMAND for protocol deviations (Epic 5)
Layer 4: + casehub-ledger — FDA Merkle tamper-evident audit trail (Epic 4)
Layer 5: + casehub-engine — IRB gate as engine PlanItem; CRITICAL deviation path (Epic 6)
Layer 6: trial-level blackboard aggregation — DSMB rollup via cross-site signal detection (Epic 3)
Layer 7: trust routing — ClinicalTrustRoutingPolicyProvider, SusarAgentAttestationWriter (LedgerAttestation), RegulatorySubmissionCaseService (IND 21 CFR 312.32), AeEscalationCompletedEvent.unexpected (casehubio/clinical#8, 2026-06-15)
Layer 8: ActionRiskClassifier oversight gate + GDPR consent withdrawal — ClinicalActionRiskClassifier, SusarCriteriaEvaluator, SusarGateDecisionListener, ConsentWithdrawalService, ClinicalComplianceSupplement (EU AI Act Art.12) (casehubio/clinical#47, #76, #77, #7, 2026-06-13)
Layer 9: Showcase — eligibility screening (EligibilityScreeningService, eligibility-screening.yaml, IRB gate), protocol amendment (ProtocolAmendmentAdvisor SPI, protocol-amendment.yaml, LlmProtocolAmendmentAdvisor via AgentProvider clinical#86) (casehubio/clinical#10, 2026-06-18)
Layer 10: IND deadline enforcement — regulatory-submission.yaml capability→humanTask with expiresAtExpression engine SPI (ExpressionEngine.extractString, HumanTaskTarget.expiresAtExpression, engine#549), ClinicalIndReportingBreachPolicy (stateless two-tier SlaBreachPolicy), RegulatorySubmissionCompleted/BreachListener, IndReportFiled/BreachLedgerEntry, V2026/V2027 (casehubio/clinical#83, 2026-06-22)
```

**Note on reading order vs build order:** Layers 2 and 4 were built in the same epic (Epic 4) — reading order differs from build order. LAYER-LOG.md preserves reading order.

**No `NaiveXxxService @DefaultBean` pattern:** Clinical uses Active Record entities directly (no downstream JPA consumers). CDI displacement is not the mechanism here — each layer adds new services and routes through them. The gaps are structural and documented in LAYER-LOG.md. See LAYER-LOG.md architectural note.

---

## Ecosystem Conventions

**Quarkus version:** All projects use `3.32.2`. When bumping, bump all projects together.

**GitHub Packages — dependency resolution:**
```xml
<repository>
  <id>github</id>
  <url>https://maven.pkg.github.com/casehubio/*</url>
  <snapshots><enabled>true</enabled></snapshots>
</repository>
```
CI must use `server-id: github` + `GITHUB_TOKEN` in `actions/setup-java`.

**Java on this machine:**
```bash
JAVA_HOME=$(/usr/libexec/java_home -v 26)
JAVA_HOME=/Library/Java/JavaVirtualMachines/graalvm-25.jdk/Contents/Home  # native only
```

**Use `mvn` not `./mvnw`** — maven wrapper not configured on this machine.

**Flyway migration structure (clinical-specific):**
casehub-work (V1-V21+) and casehub-qhorus (V1-V9) both ship migrations at `classpath:db/migration`. When both are on the classpath, Flyway finds duplicate version numbers and fails at startup. Clinical avoids this by placing migrations in datasource-scoped subdirectories:

- `db/migration/default/` — clinical domain migrations (V100-V123+). Default datasource Flyway configured as: `quarkus.flyway.locations=classpath:db/migration/default`
- `db/migration/qhorus/` — clinical ledger subclass join tables (V2000+). qhorus datasource Flyway configured as: `quarkus.flyway.qhorus.locations=classpath:db/migration,classpath:db/ledger/migration,classpath:db/migration/qhorus` (includes qhorus jar migrations and casehub-ledger migrations)

Version range conventions still apply within each directory:
- V100-V999: clinical domain tables (default datasource)
- V2000+: consumer-owned ledger subclass join tables (qhorus datasource); V1000-V1007 are casehub-ledger base tables (reserved; do not use in clinical)

**Tests use `drop-and-create` + Flyway disabled.** Both H2 databases use `quarkus.flyway.migrate-at-start=false` and `quarkus.hibernate-orm.database.generation=drop-and-create`. The classpath migration collision cannot be resolved in tests without excluding JARs from scanning. AML has the same latent issue — tracked casehubio/aml#20.

**Two-datasource architecture:**
clinical uses two persistence units:
- **Default datasource** — clinical domain entities (`io.casehub.clinical.entity`) + casehub-work entities (`io.casehub.work.runtime` — full package, not just `.model`)
- **`qhorus` named datasource** — qhorus entities (`io.casehub.qhorus.runtime`) + casehub-ledger entities + clinical ledger subclasses (`io.casehub.clinical.ledger`); directed by `casehub.ledger.datasource=qhorus`

**LedgerEntry subclasses** (e.g. `AdverseEventLedgerEntry`, `ProtocolDeviationLedgerEntry`) must live in `io.casehub.clinical.ledger`, NOT in `io.casehub.clinical.entity`. Panache entities cannot span two persistence units — if the same package is listed in both PU package configs, Quarkus throws `IllegalStateException` at build time.

**CDI wiring:** `JpaLedgerEntryRepository` is `@Alternative`. Add to production `application.properties`:
```properties
quarkus.arc.selected-alternatives=io.casehub.ledger.runtime.repository.jpa.JpaLedgerEntryRepository
```
Quarkus ArC ignores `beans.xml` `<alternatives>` — the config property is required.

**Test ledger repository:** Use `InMemoryLedgerEntryRepository` (casehub-ledger-memory) in `selected-alternatives`. `JpaLedgerEntryRepository` fails with "Table LEDGER_SUBJECT_SEQUENCE not found" — do not use it in tests. All ledger writer calls use `"default"` as tenantId placeholder until proper per-entity tenantId propagation is implemented. `InMemoryLedgerEntryRepository` implements the full 2-arg `LedgerEntryRepository` API and filters reads by `tenancyId` — all writes must pass the same `tenantId` value used in reads (currently `"default"`). The `ClinicalTestLedgerRepository` workaround was removed in clinical#74 once casehub-ledger-memory was updated.

**CaseMemoryStore CDI wiring:** `casehub-platform-memory-jpa` (`JpaMemoryStore`, `@ApplicationScoped`) displaces `NoOpCaseMemoryStore` (`@DefaultBean`) by classpath presence alone — no `selected-alternatives` needed in production. In tests, `casehub-platform-memory-inmem` (`InMemoryMemoryStore`, `@Alternative @Priority(1)`) is added test-scope and selected via `quarkus.arc.selected-alternatives`. Both JARs need `quarkus.index-dependency` entries (neither ships a Jandex index). `io.casehub.platform.memory.jpa` must be in `quarkus.hibernate-orm.packages` for the `memory_entry` table to be created by drop-and-create. Non-request-context writes (Quartz threads, async CDI observers) degrade to WARN until platform#79 fixes `MemoryPermissions.assertTenant()` for contexts without an active `@RequestScoped CurrentPrincipal`. No clinical code change is needed when platform#79 ships — the writes activate automatically.

**Neocortex JPA memory store:** `casehub-neocortex-memory-jpa` (`JpaMemoryStore`, `MemoryEntry`) lives in package `io.casehub.neocortex.memory.jpa` — this package must be in `quarkus.hibernate-orm.packages` for `drop-and-create` to generate the `memory_entry` table. The JAR ships Flyway migration `db/memory/migration/V1000__memory_entry.sql`; clinical's Flyway config already includes `classpath:db/memory/migration`.

**`FixedCurrentPrincipal` CDI ambiguity:** `FixedCurrentPrincipal` (`@Alternative @Priority(1)` from `casehub-platform-testing`) competes with `MockCurrentPrincipal` (`@DefaultBean` from `casehub-platform`) when test classes inject `FixedCurrentPrincipal` directly. The `@Priority(1)` annotation alone does not resolve the ambiguity in Quarkus ArC — add `io.casehub.platform.testing.FixedCurrentPrincipal` to `quarkus.arc.selected-alternatives` in test `application.properties`. Without this, CDI deployment fails with `AmbiguousResolutionException` for `CurrentPrincipal` whenever a test class declares `@Inject FixedCurrentPrincipal`.

**SPI override tests — use `@InjectMock`, not `@TestProfile + @Alternative`:** `getEnabledAlternatives()` in `QuarkusTestProfile` **replaces** (does not merge with) `quarkus.arc.selected-alternatives` from `application.properties`. Returning `Set.of(MyTestBean.class)` deactivates `InMemoryLedgerEntryRepository`, `MemoryPlanItemStore`, `MemorySubCaseGroupRepository`, `InMemoryCaseMetaModelRepository`, and `InMemoryMemoryStore` — causing startup failures. Use `@io.quarkus.test.InjectMock` on the SPI field instead; the mock replaces the CDI bean without touching selected-alternatives. See `IrbCommitteePolicySpiTest` for the reference pattern.

**`@InjectMock` replaces the CDI bean for the entire class — stub in `@BeforeEach`:** When `@InjectMock SomeService mock` is added to a multi-test class, all tests see the Mockito mock. Unstubbed String methods return `null` (Mockito default). If production code has a null-guard that triggers an alternate path (e.g. an audit failure entry), tests that never reference `mock` will silently fail with the wrong side effect. Add `when(mock.method(any())).thenReturn(safeValue)` in `@BeforeEach`; override in specific tests as needed. See `SponsorNotificationListenerTest` for the pattern.

**Multi-datasource XA:** Any `@Transactional` method writing to both datasources requires XA in **both** `application.properties` (production) and test `application.properties`:
```properties
quarkus.datasource.jdbc.transactions=xa
quarkus.datasource.qhorus.jdbc.transactions=xa
```
H2 and production JDBC both require this. Without it, Agroal throws "Failed to enlist" with no hint about the fix. `ProtocolDeviationService`, `DeviationExpirer`, and `AdverseEventService` all write cross-datasource.

**Reactive suppression:** `quarkus.datasource.reactive=false` and `quarkus.datasource.qhorus.reactive=false` are required in **both** test and production `application.properties`. Without them, `QhorusDashboardService` (which unconditionally injects `@Vetoed` reactive services) fails CDI deployment. `QhorusDashboardService` is also excluded via `quarkus.arc.exclude-types` in all profiles as belt-and-suspenders. Engine reactive SPIs (`ReactiveCaseInstanceRepository`, `ReactiveEventLogRepository`, etc.) are satisfied by `InMemoryReactive*` stores from `casehub-engine-persistence-memory` — these don't need a reactive datasource. `JpaReactive*` stores from `casehub-engine-persistence-hibernate` are excluded in all profiles (they require Hibernate Reactive Panache which needs `reactive=true`).

**Work-core strategy discovery:** `EngineStrategyResolver`'s `Instance<NamedStrategy>` does not discover beans that implement `NamedStrategy` transitively (e.g. `ContinuationPolicy implements ClaimSlaPolicy extends NamedStrategy`). `WorkCoreStrategyRegistrar` in `io.casehub.clinical.config` programmatically registers `ContinuationPolicy` (ClaimSlaPolicy, id="continuation") and `LeastLoadedStrategy` (WorkerSelectionStrategy, id="least-loaded") from `casehub-work-core` via `@Observes @Priority(1) StartupEvent`.

**After-commit CDI event firing:** `AdverseEventService.reportAdverseEvent()` uses `TransactionSynchronizationRegistry.registerInterposedSynchronization()` to fire `AdverseEventReportedEvent` in `afterCompletion(STATUS_COMMITTED)` — not inline with `fireAsync()`. Without this, async observers (`SusarOversightCaseService`, `RegulatorySubmissionCaseService`) race the transaction commit and may not see the persisted AE. Any new service that fires CDI async events inside `@Transactional` methods and expects observers to read the persisted data should use the same pattern.

**Ledger SNAPSHOT reactive services:** Fixed in ledger#92 — `LedgerVerificationService` and related services now use `Instance<ReactiveLedgerEntryRepository>` with `isResolvable()` guard. JDBC-only consumers start cleanly without `quarkus.arc.exclude-types`. No workaround needed.

**Dev-mode CDI alternative selection:** `quarkus.arc.selected-alternatives` does NOT support `%dev.` profile overrides. The workaround: select ALL alternatives (both JPA and memory) in the non-profiled `selected-alternatives`. Use profile-specific `exclude-types` to control which survive: `%dev.quarkus.arc.exclude-types` removes reactive JPA stores (memory wins); `%prod.quarkus.arc.exclude-types` removes memory stores (JPA wins). `DevSchemaInitializer` (`@IfBuildProfile("dev")`, `@Priority(1)`) creates the `ledger_subject_sequence` table before `DemoDataSeeder` runs.

**Connector CDI exclusions:** `TwilioSmsConnector` and `WhatsAppConnector` (from `casehub-connectors-core`) require external credentials (`casehub.connectors.twilio.*`, `casehub.connectors.whatsapp.*`) not present in the test environment. They are excluded via `quarkus.arc.exclude-types` in test `application.properties`. `SlackConnector` is replaced by `TestSlackConnector` via `@Mock`. When adding new connectors with required external config, add them to the exclude-types list.

**`Connector.send()` returns `boolean` (SNAPSHOT update):** The return type changed from `void` to `boolean`. Test implementations (`TestSlackConnector`, `FakeConnector`) must return `boolean`. Production callers (`DefaultSafetyOfficerNotifier`, `SponsorNotificationDeliveryService`) discard the return value — they use exception-based flow control.

**`RiskDecision.GateRequired` 6th param `resolutionType` (SNAPSHOT update):** `GateRequired(String reason, boolean reversible, CandidateSetStrategy candidateGroups, Duration expiresIn, String scope, @Nullable Class<?> resolutionType)`. `ClinicalActionType.resolutionType()` returns `null` — typed gate resolutions not yet defined for clinical.

**`ActionGateApprovedEvent` 6th param `resolutionTypeName` (SNAPSHOT update):** `ActionGateApprovedEvent(UUID caseId, String tenancyId, long gateId, String workItemResolution, String approvedBy, @Nullable String resolutionTypeName)`. Test callers pass `null` as the 6th arg.

**`GroupMembershipProvider` CDI ambiguity:** `MockGroupMembershipProvider` (casehub-platform, `@DefaultBean`) and `NoOpGroupMembershipProvider` (casehub-work, `@DefaultBean`) both implement `GroupMembershipProvider`, causing `AmbiguousResolutionException`. `quarkus.arc.exclude-types` does NOT suppress beans from Jandex-indexed JARs. Fix: `ClinicalGroupMembershipProvider` in `runtime/src/test/java/.../support/` is a concrete `@ApplicationScoped` (non-`@DefaultBean`) bean that suppresses both. Do not remove it.

**`CurrentPrincipal` CDI resolution (clinical#88):** `OidcCurrentPrincipal @RequestScoped @Alternative @Priority(100)` (casehub-platform-oidc) is the sole active `CurrentPrincipal`. Tenant identity comes from the JWT `tenancyId` claim. Platform#111 shipped `@Alternative @Priority(100)` on `OidcCurrentPrincipal`, which automatically displaces all non-alternative `CurrentPrincipal` implementations (`QhorusInboundCurrentPrincipal`, `TenantScopedPrincipal`). No `%prod.quarkus.arc.exclude-types` entries needed. `FixedCurrentPrincipal @Alternative @Priority(200)` (casehub-platform-testing) wins in tests.

**OIDC + `@RolesAllowed` (clinical#88):** `casehub-platform-oidc` on classpath; `quarkus-test-security` (test scope). `ClinicalGroups` constants in `api/` — `SPONSOR`, `INVESTIGATOR`, `COORDINATOR`, `MONITOR`. All 20 REST endpoints carry `@RolesAllowed`. `quarkus.security.deny-unannotated-members=true` catches unannotated methods on annotated classes (`DenyUnannotatedPredicate` scope — new classes with zero annotations are NOT covered). Dev mode: `auth.enabled-in-dev-mode=false` disables enforcement. Tests: `@TestSecurity(user = "test-actor", roles = {SPONSOR, INVESTIGATOR, COORDINATOR})` on all HTTP test classes; `FixedCurrentPrincipal` continues to handle business logic identity. `RbacBoundaryTest` uses direct Panache entity creation in `@BeforeEach` (method-level `@TestSecurity` applies to entire test lifecycle including `@BeforeEach`). `MissingTenancyException` mapped to HTTP 400 via `MissingTenancyExceptionMapper` (clinical#89).

**qhorus MessageObserver SPI (clinical#140):** `PiResponseListener` implements `MessageObserver` (qhorus SPI) — NOT `@ObservesAsync`. The qhorus `MessageObserverDispatcher` calls `observer.onMessage()` on beans implementing `MessageObserver`, not via CDI async events. Uses `@Transactional(TxType.REQUIRES_NEW)` because the dispatcher fires in `afterCompletion` where no JTA transaction is active. **pi-oversight channels must include `EVENT` in `allowedTypes`** — `receiveHumanMessage` dispatches an internal EVENT-type message as part of CDI delivery; omitting EVENT causes `MessageTypeViolationException`. See `ProtocolDeviationService.CHANNEL_ALLOWED_TYPES`.

**`casehub-platform-agent-api` dependency scope (clinical#86):** The parent POM manages this artifact with `runtime` scope. Adding it without explicit `<scope>compile</scope>` silently inherits `runtime` — classes invisible at compile time. Always use explicit `<scope>compile</scope>` when promoting a managed runtime dependency.

**Engine Quartz worker thread transaction context (clinical#86):** Worker functions registered via `Worker.builder().function()` execute on Quartz scheduler threads with no JTA transaction or CDI request context. Panache Active Record calls fail. Wrap in `QuarkusTransaction.requiringNew().call(...)`.

**`ChannelService.create()` — use `ChannelCreateRequest` for `allowedTypes`:** The 9-argument overload with a trailing `String allowedTypes` was removed in a qhorus SNAPSHOT. Use `ChannelCreateRequest` instead: `channelService.create(new ChannelCreateRequest(name, desc, semantic, null, null, null, null, null, ALLOWED_TYPES_SET, null, null, null, null, null))` where `ALLOWED_TYPES_SET` is `Set<MessageType>`. `ProtocolDeviationService.CHANNEL_ALLOWED_TYPES` is now `Set<MessageType>` (not `String`). The old form is a compile-time error on newer qhorus SNAPSHOTs.

**`LedgerErasureService.erase()` requires `ErasureReason` second argument (SNAPSHOT update):** `ConsentWithdrawalService` calls `ledgerErasureService.erase(enrollmentId.toString(), ErasureReason.GDPR_ART_17_REQUEST)`. A ledger SNAPSHOT update added a mandatory `ErasureReason` second argument — calls without it will not compile. `ErasureReason` is an enum in the casehub-ledger API; available values include `GDPR_ART_17_REQUEST`, `RETENTION_EXPIRED`, `ACCOUNT_DELETION`.

**`ConsentWithdrawalService.withdraw()` returns `WithdrawalResult` (clinical#79):** The method returns `WithdrawalResult` enum (WITHDRAWN / ALREADY_WITHDRAWN), not void. Idempotent — multiple calls return ALREADY_WITHDRAWN. Throws `PatientEnrollmentNotFoundException` if the patient does not exist. `GdprErasureResource` at `DELETE /api/gdpr/erasure/patients/{patientId}` requires `SPONSOR` or `COORDINATOR` role; returns HTTP 204 on WITHDRAWN, 200 on ALREADY_WITHDRAWN.

**`ClinicalComplianceSupplement` + `LedgerEntry.attach()` (EU AI Act Art.12):** All six AI-agent decision ledger entry writers call `ClinicalComplianceSupplement.<type>()` and attach via `entry.attach(supplement)` before `ledgerEntryRepository.save()`. The method is `attach()` — not `addSupplement()`. Use the runtime type `io.casehub.ledger.runtime.model.supplement.ComplianceSupplement` (from `casehub-ledger`) — the API model (`casehub-ledger-api`) is a plain POJO and cannot be persisted via the `@OneToMany supplements` relationship. `casehub.ledger.identity.tokenisation.enabled` must remain `false` in test `application.properties` — enabling it tokenizes `actorId` fields on save, breaking existing tests that assert raw actor ID strings (e.g., `PiResponseListenerTest`).

**`CaseInstanceCache` gate discrimination race condition:** When writing `@ConsumeEvent` handlers for `casehub.action.gate.rejected/approved/expired`, do NOT use `CaseInstanceCache.get(caseId).getPendingActionGate()` to discriminate which type of gate fired. The engine's `ActionGateRejectedHandler` and `ActionGateExpiredHandler` (both `@ConsumeEvent(blocking = true)`) clear `pendingActionGate` synchronously before publishing downstream — by the time clinical's consumer runs, `pendingActionGate` is null. Use a DB query instead: `AdverseEvent.findBySusarOversightCaseId(event.caseId())` returns non-null only for SUSAR oversight gates, is race-free, and also provides `aeId`/`enrollmentId`/`grade` without secondary lookups. See `SusarGateDecisionListener` for the reference pattern.

**pi-oversight channel name format:** `clinical/deviation/dev-<UUID>/pi-oversight` — the `dev-` prefix is required because a qhorus snapshot tightened `ChannelSlugValidator` to require `[a-z][a-z0-9]*(-[a-z0-9]+)*` on all segments. UUID segments start with hex digits and violate the `[a-z]` start requirement without the prefix. `PiResponseListener.CHANNEL_PATTERN` extracts the UUID from group-1 after `dev-`: `"clinical/deviation/dev-([0-9a-f-]+)/pi-oversight"`. Tests and helper classes that hardcode channel names must use the `dev-` prefix. Tracked: casehubio/clinical#63.

**`SponsorNotificationRetryJob` scheduler exclusion in tests:** The durable sponsor notifier's retry scheduler is excluded from `@QuarkusTest` contexts via `quarkus.arc.exclude-types=...io.casehub.clinical.service.SponsorNotificationRetryJob`. Tests drive delivery directly via `SponsorNotificationDeliveryService.attemptDelivery()`. Without this exclusion, the scheduler fires on every tick during tests and competes with test-driven delivery calls.

**`SiteEnrollmentTrajectoryJob` scheduler exclusion in tests:** Same pattern as `SponsorNotificationRetryJob` — excluded via `quarkus.arc.exclude-types` in test `application.properties`. The job snapshots enrollment trajectories as CBR cases on a configurable interval (`casehub.clinical.enrollment-trajectory.snapshot-interval`, default 24h).

**`TrialSafetyAggregationJob` scheduler in tests:** NOT excluded from `quarkus.arc.exclude-types` — integration tests (`DsmbBatchSignalWorkItemTest`) inject the real bean to test WorkItem creation. The scheduler is prevented from firing by setting `casehub.clinical.trial-safety.interval=9999h` in test `application.properties`. The job scans AE entities per site to detect grade-threshold and cross-site-cluster safety signals and creates DSMB WorkItems for batch-detected signals.

**`CbrRetentionPurgeJob` scheduler exclusion in tests:** Same pattern — excluded via `quarkus.arc.exclude-types` in test `application.properties`. The job runs weekly (configurable via `casehub.clinical.cbr.retention.interval`, default 168h) and purges CBR cases per domain based on `max-age-days` and `max-cases` config properties.

**`CbrCompactionJob` scheduler exclusion in tests:** Same pattern — excluded via `quarkus.arc.exclude-types` in test `application.properties`. The job runs weekly (configurable via `casehub.clinical.cbr.compaction.interval`, default 168h) and merges similar CBR cases into weighted representatives based on exact categorical merge key match. Disabled by default (`casehub.clinical.cbr.compaction.enabled=false`).

**`PlanTrace` 7th param `variantId` (SNAPSHOT update):** `PlanTrace` record in `casehub-neocortex-memory-api` gained a 7th `variantId` parameter (nullable). All constructors — `new PlanTrace(bindingName, capabilityName, workerName, stepOutcome, priority, parameters, variantId)` — require the extra arg. Clinical callers pass `null`.

**CBR `eventType` field is `categoricalList` not `categorical`:** `ClinicalCbrSchemaInitializer` registers `eventType` as `FeatureField.categoricalList("eventType")`. Feature builders (`AeCbrFeatureBuilder`, `DemoDataSeeder.seedTrajectoryCase`) must store it as `List.of(value)`, not plain `String`. `AeTrajectoryAlertService` and `TrialDashboardResource` query with `CbrFilter.contains()` which requires `CategoricalList`.

**`WorkItem` is now a Java record in `io.casehub.work.api` (SNAPSHOT update):** Fields are accessed via record accessors (`id()`, `callerRef()`, `status()`, `payload()`, `resolution()`, etc.) not field access (`.id`, `.callerRef`). `WorkItemEntity` (JPA entity) remains in `io.casehub.work.runtime.model` for direct persistence. `WorkItemStore.scanAll()` returns `List<WorkItem>` (API records). `WorkItemLifecycleEvent` moved from `io.casehub.work.runtime.event` to `io.casehub.work.api`. Tests construct `WorkItem.builder().id(uuid).status(status).build()` instead of `new WorkItemEntity()`.

**`NormalisedMessage` gained `payload` field at position 3 (SNAPSHOT update):** Constructor is now `(MessageType, String content, String payload, String senderInstanceId, String correlationId, Long inReplyTo, List<ArtefactRef> artefactRefs, String target)`. Callers that previously passed `(type, content, senderInstanceId, correlationId, null, null, null)` must insert `null` for `payload` at position 3.

**`ChannelResource` CDI exclusion required:** `ChannelResource` (qhorus runtime) injects `QhorusDashboardService`. Excluding `QhorusDashboardService` alone is insufficient — `ChannelResource` must also be excluded. Full chain: both in `quarkus.arc.exclude-types`.

**`CallbackRegistry` CDI exclusion:** Platform SNAPSHOT added callback infrastructure (`LeaseReaper`, `CallbackRegistrationResource`, `CallbackActionRiskClassifier`, `CallbackWorkerProvisionerDecorator`). Clinical doesn't use callbacks — all four excluded in test `application.properties`.

**`QuartzRetryService` + `QuartzWorkerExecutionJob` CDI exclusion:** Engine SNAPSHOT added `RecoveryCoordinator` SPI. `QuartzRetryService` injects it; `QuartzWorkerExecutionJob` injects `QuartzRetryService`. Both excluded in test `application.properties`.

**`casehub.signal.api-url` and `casehub.signal.number` test config:** Engine SNAPSHOT added required signal config properties. Set to placeholder values in test `application.properties` (`http://localhost:8080` and `+10000000000`).

**H2 reserved-word columns:** `value` and `type` are H2 reserved words. Entity fields mapped to `result_value` and `vital_type` column names via `@Column(name = ...)`. Flyway migrations use the renamed columns.

**Engine CDI wiring (Layer 5+):** When adding `casehub-engine` to the classpath, also add `casehub-platform` and `casehub-platform-expression` — without them, engine beans (`JQEvaluator`, event handlers) cannot resolve their injection points and CDI startup fails.

```xml
<dependency><groupId>io.casehub</groupId><artifactId>casehub-platform</artifactId></dependency>
<dependency><groupId>io.casehub</groupId><artifactId>casehub-platform-expression</artifactId></dependency>
<dependency><groupId>io.casehub</groupId><artifactId>casehub-platform-testing</artifactId><scope>test</scope></dependency>
```

Add `casehub-engine-persistence-hibernate` as a production dependency — provides JPA implementations for `CaseInstanceRepository`, `EventLogRepository`, `SubCaseGroupRepository`, `CaseMetaModelRepository`. `casehub-engine-persistence-memory` is compile-scope (explicit `<scope>compile</scope>` overrides parent POM's `dependencyManagement` test scope). All its beans are `@Alternative` — inactive unless selected via `quarkus.arc.selected-alternatives`. Dev mode selects memory stores; production excludes them via `%prod.quarkus.arc.exclude-types`.

In production `application.properties`, add:
```properties
# JPA alternatives
quarkus.arc.selected-alternatives=\
  io.casehub.ledger.runtime.repository.jpa.JpaLedgerEntryRepository,\
  io.casehub.persistence.jpa.JpaPlanItemStore,\
  io.casehub.persistence.jpa.JpaSubCaseGroupRepository

# Engine CDI — index engine jars (no embedded Jandex)
# Note: CasehubWorkloadProvider was deleted in engine#378 — no WorkloadProvider exclude needed in prod.
%prod.quarkus.index-dependency.casehub-engine.group-id=io.casehub
%prod.quarkus.index-dependency.casehub-engine.artifact-id=casehub-engine
%prod.quarkus.index-dependency.casehub-engine-common.group-id=io.casehub
%prod.quarkus.index-dependency.casehub-engine-common.artifact-id=casehub-engine-common
%prod.quarkus.index-dependency.casehub-engine-persistence-hibernate.group-id=io.casehub
%prod.quarkus.index-dependency.casehub-engine-persistence-hibernate.artifact-id=casehub-engine-persistence-hibernate
```

In test `application.properties`:
- Use memory stores: `quarkus.arc.selected-alternatives` must include `MemoryPlanItemStore,MemorySubCaseGroupRepository`
- Index engine jars: `quarkus.index-dependency.engine-runtime.artifact-id=casehub-engine` (and work-adapter, blackboard, testing, persistence-memory, engine-common)
- **Quartz cron incompatibility:** `casehub-engine-scheduler-quartz` brings `quarkus-quartz` which requires 6-7 field cron. `casehub-work` scheduler beans (`ExpiryCleanupJob`, `ExpiryTimerJob`, `ClaimDeadlineJob`, `ClaimDeadlineTimerJob`, `RoutingCursorCleanupJob`) use 5-field Unix cron — they fail at startup with Quartz. Exclude them via `quarkus.arc.exclude-types`. Note: `ExpiryTimerJob` and `ClaimDeadlineTimerJob` are renamed equivalents of `ExpiryCleanupJob` and `ClaimDeadlineJob` in recent casehub-work SNAPSHOTs — exclude all four. Also set `quarkus.quartz.store-type=ram` and `quarkus.scheduler.start-mode=forced`.
- **YAML binding conditions:** Use `on.contextChange.filter` not `when` — the `when` field is silently ignored for `contextChange` triggers (engine#335).
- **JQ `to_entries` iteration:** Use `to_entries[]` (with `[]`) not `to_entries` when piping to `select`. Without `[]`, `select` tests the whole array as a single value (always false). Example: `[.myMap // {} | to_entries[] | select(.value == true)] | length >= 2`.
- **Engine case activation — three-phase pattern:** Any service that calls `startCase().toCompletableFuture().join()` must NOT be `@Transactional` at that call site. Split into three separate `@Transactional` calls: (1) validate + update domain status, (2) call `startCase().join()` outside any transaction boundary, (3) persist the returned `caseId`. Holding a DB connection across `join()` deadlocks the Agroal pool when the engine's JPA persistence also needs a connection from the same pool. See `TrialActivationService` for the reference implementation.
- **inputMapping not inputProjection:** YAML humanTask bindings use `inputMapping` field (mini-DSL, not JQ) — the field sets the WorkItem payload. `outputMapping` uses JQ flat pattern `"{ key: . }"` (engine#314: nested `{..}` unsupported).
- **YAML worker capability binding — `capability: name` directly, NOT `worker: { capability: ... }`:** `io.casehub.model.Binding` has `capability: String` as a direct schema field; there is no `worker:` field. Jackson silently drops unknown keys (`FAIL_ON_UNKNOWN_PROPERTIES = DISABLED`), so `worker: { capability: safety-monitoring }` is parsed without error but ignored — `schemaBinding.getCapability()` returns null and `convertBinding()` throws `IllegalArgumentException: must have capability, subCase, or humanTask`. The correct structure: define `spec.capabilities[- name/inputProjection]` at the YAML top level (the `inputProjection` is a JQ expression applied to the case context to produce worker input), then reference `capability: name` directly on the binding. A Java-function worker ALSO requires programmatic registration in `getDefinition()` override — the YAML alone cannot express a Java-function worker; use `.function(evaluator)` on the builder. See `ClinicalSusarOversightCaseHub` + `susar-oversight.yaml` for the reference pattern.
- **YAML completion expression required for goals:** Engine SNAPSHOT added `DefaultCaseDefinitionRegistry.validateExpressions()` — every goal defined in a YAML case definition must be referenced in a `completion` expression. Unreferenced goals fail at startup with `IllegalArgumentException: Goal 'X' is not referenced in any completion expression`. Previously unreferenced goals were silently ignored. Add `completion: { success: { allOf: [goal-name] } }` to every YAML with goals.
- **WorkloadProvider stub:** `casehub-work` injects `WorkloadProvider`. The engine's `CasehubWorkloadProvider` (the bridge) was deleted in engine#378. Clinical provides `StubWorkloadProvider` (`@DefaultBean @ApplicationScoped`, returns 0) in `runtime/src/test/java/.../support/`. `JpaWorkloadProvider` is excluded via `quarkus.arc.exclude-types`. Any new test module that adds engine must add the stub (clinical#41).
- **CaseLifecycleEvent observers — accept GoalReached in tests:** The in-memory engine does not reliably fire `CaseCompleted` CDI events in `@QuarkusTest` (engine#393). `GoalReached` fires first and is reliable. CDI observers that need to react to case completion should accept both `"GoalReached"` and `"CaseCompleted"` event types, with an idempotency guard (since `GoalReached` fires once per goal, not once per case). See `AeEscalationListener` for the reference pattern.
- **Direct entity creation — stamp `tenantId = principal.tenancyId()` in `@BeforeEach`:** Any `@QuarkusTest` that creates domain entities directly (not via REST) and later calls services that query by tenant must stamp `ae.tenantId = principal.tenancyId()` (or `trial.tenantId`, etc.) in `@BeforeEach`. Entities created without stamping get the field default `"default"`, which mismatches `FixedCurrentPrincipal.tenancyId()`. Missing the stamp causes `SecurityException` from `InMemoryMemoryStore.query()` or `MemoryPermissions.assertTenant()`, which propagates through the `@Transactional` boundary of `prepareAndMarkRequested()` and marks the JTA transaction as rollback-only — the test fails with no obvious error. Reference patterns: `DsmbRollupTest.persistTrial()`, `AeEscalationContextInjectionTest.setup()`, `AeEscalationLifecycleTest.setup()`, `SusarOversightLifecycleTest.persistAe()`.
- **`@Transactional` lifecycle tests and entity creation:** `@ObservesAsync` service methods that call `AdverseEvent.findById()` or similar Panache lookups in Phase 1 require the entity to exist before the observer fires. Add entity creation to `@BeforeEach @Transactional setup()` in `@QuarkusTest` lifecycle tests — not just random UUIDs. See `AeEscalationLifecycleTest` and `IrbGateLifecycleTest` for the pattern.
- **Integration tests for `@ObservesAsync @Transactional` listeners:** call the listener method directly (e.g., `listener.onAeReported(event)`) rather than through `Event.fireAsync()`. `REQUIRES_NEW` methods called from within the listener complete synchronously from the test thread — no Awaitility needed. Entity setup requires only what the listener actually loads from the DB (e.g., `ClinicalTrial` + `TrialSite` for notification listeners, not `AdverseEvent`). `@Mock @Singleton` test connector beans (e.g., `TestSlackConnector`) persist across tests — call `reset()` in `@BeforeEach`. See `SafetyOfficerNotificationIntegrationTest` for the reference pattern.
- **`CaseInstanceRepository.findByUuid` takes `(UUID caseId, String tenancyId)` (June 1 engine snapshot).** Production callers pass `event.tenancyId()`. Passing `null` for tenancyId causes `NullPointerException` inside `InMemoryCaseInstanceRepository` when the case is found (calls `tenancyId.equals(...)` on the parameter). In lifecycle tests, prefer asserting domain state (`escalationStatus`, `approvalDecision()`) over calling `findByUuid` — domain assertions are sufficient and avoid the tenancyId resolution problem entirely.

**casehub-pages DSL conventions (webui TypeScript):**
- `columns(distribution: number[], ...slotContents: Component[][])` — first arg is distribution array `[3,3,3,3]`, each slot is a Component array
- `page(name, ...components, pageOptions?)` — `{ datasets: [...] }` must be the LAST argument
- `lookup(dataSetId: string, ...ops)` — first arg is string ID (not dataset object); operations are direct args (not wrapped in arrays)
- `groupBy(source: null | string, ...resultColumns)` — source is `null` (no grouping) or string column name
- `actionButton()` and `alert()` helpers in `webui/src/helpers.ts` create native `action-button` and `alert` web components — prefer over `html()` with inline scripts
- Custom web components in `webui/src/components/` (`ClinicalPiApproval`, `ClinicalSusarGate`, `ClinicalMerkleVerify`) — light DOM, attribute-driven, registered in `index.ts` before `loadSite()`. Use for interactive elements that need pre-fetch, response display, or GET actions (capabilities `actionButton()` lacks)

## Frontend Dependencies

This project consumes frontend packages from casehub-pages and blocks-ui via **Maven SNAPSHOT** artifacts (WebJar pattern).
See [casehub-pages ADR-0001](https://github.com/casehubio/casehub-pages/blob/main/docs/adr/0001-cross-repo-frontend-dependency-management.md).

| Source | Mechanism |
|--------|-----------|
| casehub-pages | Maven SNAPSHOT (`META-INF/resources/`) |
| blocks-ui | Maven SNAPSHOT (`META-INF/resources/`) |

**Local development:** after changing pages or blocks-ui, run `yarn build && mvn install` in the source repo to publish the SNAPSHOT to `~/.m2`.

**Do not use npm `file:` references for cross-repo dependencies** — they break in CI. See ADR-0001.

## Build Commands

```bash
# Build and test api/ module only
mvn install -pl api --batch-mode

# Build and test runtime/ module (api must be installed first if not cached)
mvn install -pl api --batch-mode && mvn test -pl runtime --batch-mode

# Full reactor test (both modules)
mvn test --batch-mode

# Test a single class (runtime)
mvn test -pl runtime -Dtest=TrialResourceTest --batch-mode

# Compile only (no tests)
mvn compile -pl api,runtime --batch-mode
```

**Important:** `mvn test -pl runtime` requires `api` to be installed in the local Maven repo first. Run `mvn install -pl api` if you get ClassNotFound errors for `io.casehub.clinical.api.*`.

---

## Development Workflow

### Platform Coherence
Before implementing any feature, SPI, data model, or abstraction — run the Platform Coherence Protocol in `../parent/docs/PLATFORM.md`. Check capability ownership, boundary rules, and consistency with existing patterns. Update platform docs if new patterns are established.

### TDD
Every implementation plan must include tests at all levels:
- **Unit tests** — pure logic, no I/O, fast
- **Integration tests** (`@QuarkusTest` with H2) — Panache, REST, CDI wiring
- **End-to-end tests** — full stack, happy path through the full trial scenario
- **Robustness tests** — boundary conditions, invalid input, missing data
- **Correctness tests** — SLA deadline computation, state machine transitions, JQ mappings

Tests are not optional and are not deferred to a follow-up. They are part of the implementation plan from the start.

### IntelliJ MCP Tools
Two IntelliJ MCPs are available: `mcp__intellij` and `mcp__intellij-index`.

**Always check both are available before starting implementation work.** If either is unavailable, stop and report before proceeding.

**Prefer IntelliJ tools over Bash** for all operations they support — file creation, symbol search, rename refactoring, find references, go to definition, build, diagnostics. IntelliJ tools are more correct, context-aware, and less error-prone than shell equivalents.

### Code Review
Before marking any task complete, invoke `superpowers:requesting-code-review` to review the implementation for quality, correctness, and platform consistency.

### Documentation Maintenance
After every implementation session:
- Revise all affected documentation to reflect code changes
- Check cross-references are correct (file paths, class names, issue numbers)
- Address drift, staleness, redundancy, duplication, and gaps
- Update platform docs if new patterns or conventions were established
- Update CLAUDE.md if build commands, conventions, or structure changed
