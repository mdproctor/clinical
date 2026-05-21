# clinical Workspace

**Name:** clinical

**Project repo:** /Users/mdproctor/claude/casehub/clinical
**Workspace type:** public

## Session Start

Run `add-dir /Users/mdproctor/claude/casehub/clinical` before any other work.

## Artifact Locations

| Skill | Writes to |
|-------|-----------|
| brainstorming (specs) | `specs/` |
| writing-plans (plans) | `plans/` |
| handover | `HANDOFF.md` |
| idea-log | `IDEAS.md` |
| design-snapshot | `snapshots/` |
| java-update-design / update-primary-doc | `design/JOURNAL.md` (created by `epic`) |
| adr | `adr/` |
| write-blog | `blog/` |

## Structure

- `HANDOFF.md` — session handover (single file, overwritten each session)
- `IDEAS.md` — idea log (single file)
- `specs/` — brainstorming / design specs (superpowers output)
- `plans/` — implementation plans (superpowers output)
- `snapshots/` — design snapshots with INDEX.md (auto-pruned, max 10)
- `adr/` — architecture decision records with INDEX.md
- `blog/` — project diary entries with INDEX.md
- `design/` — epic journal (created by `epic` at branch start)

## Git Discipline

Two git repositories are active in every session:
- **Workspace** (`/Users/mdproctor/claude/public/casehub/clinical`) — methodology artifacts: handover, blog, specs, plans, ADRs
- **Project repo** (`/Users/mdproctor/claude/casehub/clinical`) — source code

Before any git operation, run `git rev-parse --show-toplevel` to confirm which repo is currently active. Do not assume — the session may have opened in either. cd to the correct repo before staging:
- Source code commits → project repo
- Methodology artifacts → workspace


## Rules

- All methodology artifacts go here, not in the project repo
- Promotion to project repo is always explicit — never automatic
- Workspace branches mirror project branches — switch both together

## Peer Repos — Hard Boundary

**This session owns exactly two repos: the workspace and the project repo.**
Every other casehubio repo is a peer repo with its own Claude session.

Peer repos (never commit or push to these from this session):
- `/Users/mdproctor/claude/casehub/parent` and all paths under it
- `/Users/mdproctor/claude/casehub/engine`
- `/Users/mdproctor/claude/casehub/ledger`
- `/Users/mdproctor/claude/casehub/work`
- `/Users/mdproctor/claude/casehub/qhorus`
- `/Users/mdproctor/claude/casehub/connectors`
- `/Users/mdproctor/claude/casehub/devtown`
- `/Users/mdproctor/claude/casehub/aml`
- Any other sibling directory under `/Users/mdproctor/claude/casehub/`

**When a cross-repo doc change is needed** (e.g. `docs/PLATFORM.md`,
`docs/repos/casehub-clinical.md` in the parent): file a GitHub issue on
`casehubio/parent` describing the change — never edit or commit directly.

Skills that check this (implementation-doc-sync, work-end, handover) must
read this section before deciding where to commit doc changes.

## Routing

| Artifact   | Destination | Notes |
|------------|-------------|-------|
| adr        | project     | lands in `docs/adr/` — promoted at epic close |
| specs      | project     | lands in `docs/specs/` — promoted at epic close |
| blog       | workspace   | staged here; published to mdproctor.github.io via publish-blog |
| plans      | workspace   | stay in workspace permanently |
| design     | workspace   | epic journal stays in workspace |
| snapshots  | workspace   | stay in workspace permanently |
| handover   | workspace   | |

---

# casehub-clinical — Claude Code Project Guide

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

## Project Type

type: java

**Stack:** Java 21 (on Java 26 JVM), Quarkus 3.32.2, GraalVM 25 (native image target)

---

## Agentic Harness Goals

**Read first:** `../parent/docs/AGENTIC-HARNESS-GUIDE.md`

**Primary goal:** Reference architecture and field showcase for Java developers in regulated healthcare — demonstrating that GCP, FDA, and GDPR requirements are structurally satisfied by CaseHub where workflow-based LLM systems cannot.

**Secondary goal:** LLM and human tutorial material, produced as a by-product of building the application correctly. The tutorial structure emerges from the layered adoption sequence — do not design for the tutorial.

**LAYER-LOG.md** (`LAYER-LOG.md` at project root) is the primary new artifact. A layer is not complete until its entry is written. See the AML reference implementation and `docs/protocols/universal/layer-log.md` in casehub-parent for the format.

---

## What This Project Is

`casehub-clinical` is an **agentic harness for clinical trial coordination** built on the CaseHub platform foundation. It coordinates eligibility screening agents, safety monitoring agents, PI authorisation gates, and IRB approval gates — producing an FDA-compliant, GDPR-aware, independently verifiable audit trail. Field showcase and tutorial for Java developers in regulated healthcare.

This is an **application layer**, not a framework. The foundation provides coordination, accountability, audit, and compliance primitives. casehub-clinical provides the clinical trial domain logic: what a trial protocol is, how a site manages patient enrollment, how adverse events escalate, and how the FDA audit trail is constructed.

### Why Clinical Trials

Clinical trials operate under the strictest regulated AI requirements of any domain: GCP (ICH E6(R3)), FDA IND requirements, EMA CTR, and GDPR for patient data. Every agent decision must be traceable. Every protocol deviation must be authorised by a named Principal Investigator with a formal commitment. Every adverse event has a hard reporting deadline (24 hours for serious events, 7 days for others) with documented escalation.

The specific compliance gap ClinicalAgent (arXiv 2404.14777) — the peer-reviewed open-source baseline — cannot close by adding features:

| GCP / ICH / FDA requirement | ClinicalAgent | casehub-clinical |
|---|---|---|
| Adverse event SLA — serious within 24h, others within 7 days | No deadline tracking | WorkItem `claimDeadline` with auto-escalation |
| Protocol deviation authorisation — PI must formally approve | Agent decides autonomously; no named responsible party | COMMAND from PI required; commitment lifecycle tracks acknowledgement and resolution |
| Consent withdrawal cascade — GDPR Art.17 patient data erasure | No GDPR capability | `LedgerErasureService` + `DecisionContextSanitiser` SPI |
| Multi-site independence — 50+ sites with independent rollup to trial level | Single-case linear pipeline | Sub-case orchestration per site with trial-level aggregation |
| Tamper-evident audit — FDA audit trail independently verifiable | No audit trail | Merkle Mountain Range + Ed25519-signed checkpoints |
| Trust-weighted safety agent routing — reliable agents on high-risk decisions | No trust model | Bayesian Beta + EigenTrust via `TrustWeightedSelectionStrategy` |

**Comparison baseline:** ClinicalAgent ([arXiv 2404.14777](https://arxiv.org/abs/2404.14777), ACM BCB '24, GitHub open source). Full gap analysis in `docs/use-case-analysis.md` in casehub-parent (§8.1). Scored 24/25 on market fit — highest of all use cases.

---

## Layering Rule

This is an application, not a framework. If the capability requires knowledge of clinical trial protocols, GCP, FDA IND, or patient consent, it belongs here. If it is purely about cases, commitments, trust, or audit records, it belongs in the foundation.

---

## Reference Documents (in casehub-parent)

| Document | What it covers |
|----------|---------------|
| `../parent/docs/AGENTIC-HARNESS-GUIDE.md` | Goals, what to produce, retroactive work instructions, layer maintenance |
| `../parent/docs/repos/casehub-clinical.md` | Harness structure, tutorial layers table, layer status |
| `../parent/docs/use-case-analysis.md` | Use case scoring, clinical trial selection rationale (§8.1), GCP compliance gap analysis |
| `../parent/docs/tutorial-strategy.md` | Clinical tutorial layers §7 — teaching objectives and code sketches per layer |
| `../parent/docs/protocols/casehub/HARNESS-INDEX.md` | CaseHub app protocols |
| `../parent/docs/protocols/universal/INDEX.md` | Universal Java/Quarkus protocols |

## External Reference Standards

Consult these before making domain model, compliance, or grading decisions:

| Standard / Reference | What it covers | Use for |
|----------------------|---------------|---------|
| [ICH E6(R3) GCP](https://www.ich.org/page/efficacy-guidelines) | Good Clinical Practice — authoritative source for trial conduct, adverse event reporting obligations, PI responsibilities | Compliance requirements, SLA derivation, PI authorisation obligations |
| [CTCAE v5.0](https://ctep.cancer.gov/protocoldevelopment/electronic_applications/ctc.htm) | NCI Common Terminology Criteria for Adverse Events — Grade 1–5 definitions and severity thresholds | `CtcaeGrade` enum, SLA assignments per grade |
| [21 CFR Part 312](https://www.ecfr.gov/current/title-21/chapter-I/subchapter-D/part-312) | FDA IND requirements — expedited safety reporting, protocol amendments, sponsor obligations | FDA reporting SLAs, audit trail requirements |
| [FHIR ResearchStudy / ResearchSubject](https://hl7.org/fhir/researchstudy.html) | HL7 FHIR standard data model for clinical trials and subjects | Domain model field names and relationships — canonical reference for what fields a trial/site/patient needs |
| [ClinicalAgent (arXiv 2404.14777)](https://arxiv.org/abs/2404.14777) | Peer-reviewed open-source baseline (ACM BCB '24) | Comparison baseline — what casehub-clinical must structurally exceed |
| [OpenStudyBuilder](https://github.com/NovoNordisk-OpenSource/openstudybuilder) | Open source CDISC-based clinical study management (Novo Nordisk) | Reference implementation for trial protocol and study design data models |

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
- `adverse-event-escalation` fires when safety-monitoring reports Grade ≥ 3 event — 24h WorkItem SLA
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
    → DSMB review triggers when safety signal threshold crossed across ≥ 2 sites
```

Trial-level binding fires on aggregated context from all site sub-cases — no site-level agent reasons about this; the engine detects the cross-site pattern from accumulated blackboard state.

### Foundation Gates

| Capability | Foundation prerequisite |
|-----------|------------------------|
| Site-level sub-case orchestration | engine#195 = scaffold only (SubCase model, Stage.subCases, CasePlanModel) — execution wiring (SubCaseExecutionHandler, blackboard↔runtime integration) pending engine#112 ❌ BLOCKED |
| Adverse event SLA WorkItem | casehub-work ✅ production |
| PI authorisation commitment lifecycle | P0 complete (engine#186 ✅, qhorus ✅) |
| GDPR consent withdrawal (Art.17) | LedgerErasureService ✅ |
| FDA Merkle audit trail | CaseLedgerEntry ✅ (2026-04-26) |
| EU AI Act Art.12 ComplianceSupplement | casehub-ledger ✅ |
| Trust-weighted safety agent routing | P1.3 TrustWeightedSelectionStrategy wired in engine |
| LLM protocol amendment supervisor | LlmPlanningStrategy SPI (engine) |
| HITL WorkItem → case signal (IRB gate) | casehub-work-adapter wiring pending — tracked casehubio/work#136 ❌ BLOCKED |

### Showcase Scenario

3-site oncology trial. Site A enrolls a patient — agents run eligibility screening across 12 criteria. A marginal criterion triggers an IRB consultation (WorkItem: 72-hour SLA). At Site B, a Grade 3 adverse event fires automatic 24-hour safety escalation. At Site C, a protocol amendment is proposed — the LLM supervisor reads accumulated context from all three sites and recommends whether to proceed. The Merkle audit trail means FDA can independently verify the complete decision chain for every patient at every site.

ClinicalAgent runs as a linear pipeline for one site. It has no concept of SLA, no IRB gate, no adverse event escalation, and no audit trail.

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
casehub-work (V1–V21+) and casehub-qhorus (V1–V9) both ship migrations at `classpath:db/migration`. When both are on the classpath, Flyway finds duplicate version numbers and fails at startup. Clinical avoids this by placing migrations in datasource-scoped subdirectories:

- `db/migration/default/` — clinical domain migrations (V100–V107+). Default datasource Flyway configured as: `quarkus.flyway.locations=classpath:db/migration/default`
- `db/migration/qhorus/` — clinical ledger subclass join tables (V1005+). qhorus datasource Flyway configured as: `quarkus.flyway.qhorus.locations=classpath:db/migration,classpath:db/migration/qhorus` (includes qhorus jar migrations)

Version range conventions still apply within each directory:
- V100–V999: clinical domain tables (default datasource)
- V1005+: consumer-owned ledger subclass join tables (qhorus datasource); V1000–V1004 are casehub-ledger base tables

**Tests use `drop-and-create` + Flyway disabled.** Both H2 databases use `quarkus.flyway.migrate-at-start=false` and `quarkus.hibernate-orm.database.generation=drop-and-create`. The classpath migration collision cannot be resolved in tests without excluding JARs from scanning. AML has the same latent issue — tracked casehubio/aml#20.

**Two-datasource architecture:**
clinical uses two persistence units:
- **Default datasource** — clinical domain entities (`io.casehub.clinical.entity`) + casehub-work entities (`io.casehub.work.runtime` — full package, not just `.model`)
- **`qhorus` named datasource** — qhorus entities (`io.casehub.qhorus.runtime`) + casehub-ledger entities + clinical ledger subclasses (`io.casehub.clinical.ledger`); directed by `casehub.ledger.datasource=qhorus`

**LedgerEntry subclasses** (e.g. `AdverseEventLedgerEntry`, `ProtocolDeviationLedgerEntry`) must live in `io.casehub.clinical.ledger`, NOT in `io.casehub.clinical.entity`. Panache entities cannot span two persistence units — if the same package is listed in both PU package configs, Quarkus throws `IllegalStateException` at build time.

**CDI wiring:** `JpaLedgerEntryRepository` is `@Alternative`. Add to both `application.properties` files:
```properties
quarkus.arc.selected-alternatives=io.casehub.ledger.runtime.repository.jpa.JpaLedgerEntryRepository
```
Quarkus ArC ignores `beans.xml` `<alternatives>` — the config property is required.

**Multi-datasource XA:** Any `@Transactional` method writing to both datasources requires XA in **both** `application.properties` (production) and test `application.properties`:
```properties
quarkus.datasource.jdbc.transactions=xa
quarkus.datasource.qhorus.jdbc.transactions=xa
```
H2 and production JDBC both require this. Without it, Agroal throws "Failed to enlist" with no hint about the fix. `ProtocolDeviationService`, `DeviationExpirer`, and `AdverseEventService` all write cross-datasource.

**Reactive suppression:** `quarkus.datasource.reactive=false` and `quarkus.datasource.qhorus.reactive=false` are required in test `application.properties` to prevent startup failure in the JDBC-only test environment. Do NOT add `casehub.qhorus.reactive.enabled=false` — this key no longer exists in qhorus config model and causes `ConfigValidationException`.

**Ledger SNAPSHOT reactive services:** Fixed in ledger#92 — `LedgerVerificationService` and related services now use `Instance<ReactiveLedgerEntryRepository>` with `isResolvable()` guard. JDBC-only consumers start cleanly without `quarkus.arc.exclude-types`. No workaround needed.

**Connector CDI exclusions:** `TwilioSmsConnector` and `WhatsAppConnector` (from `casehub-connectors-core`) require external credentials (`casehub.connectors.twilio.*`, `casehub.connectors.whatsapp.*`) not present in the test environment. They are excluded via `quarkus.arc.exclude-types` in test `application.properties`. `SlackConnector` is replaced by `TestSlackConnector` via `@Mock`. When adding new connectors with required external config, add them to the exclude-types list.

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

## Work Tracking

**Issue tracking:** enabled
**GitHub repo:** casehubio/clinical

**Automatic behaviours:**
- Before implementation begins — check for an active issue. If none, run issue-workflow Phase 1 before writing any code.
- Before any commit — confirm issue linkage.
- All commits reference an issue — `Refs #N` or `Closes #N`.
- All commits must also reference the parent epic — include the epic issue number in the commit message or PR description.

---

## Development Workflow

### Platform Coherence
Before implementing any feature, SPI, data model, or abstraction — run the Platform Coherence Protocol in `../parent/docs/PLATFORM.md`. Check capability ownership, boundary rules, and consistency with existing patterns. Update platform docs if new patterns are established.

### TDD
Every implementation plan must include tests at all levels:
- **Unit tests** — pure logic, no I/O, fast
- **Integration tests** (`@QuarkusTest` with H2) — Panache, REST, CDI wiring
- **End-to-end tests** — full stack, happy path through the showcase scenario
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

### External Reference Standards
Before making domain model, compliance, or grading decisions — consult the standards listed in the "External Reference Standards" section above.
