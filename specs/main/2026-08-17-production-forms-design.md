# Production Forms & Clinical Data Capture — Design Spec

**Date:** 2026-08-17
**Branch:** main (pre-branch design)
**Decisions:** [decisions.md](decisions.md)

## Overview

Transform casehub-clinical from an ops/oversight demo into a deployable reference architecture with full production data entry forms. The UI gains a complete clinical trial management surface alongside the existing review workbenches.

### Goals

1. A clinical coordinator can create a trial, add sites, enroll patients, and capture all clinical data through real forms
2. The new scenario tool can automate form submissions to drive demos through the real UI
3. Every clinical data point participates in the accountability layer (LedgerEntry, Merkle audit)
4. The app is credible as a reference architecture for regulated AI in clinical trials

### Non-goals

- Full FHIR compliance (we use FHIR naming and representative fields, not exhaustive extensions)
- Authentication/identity provider integration (OIDC infrastructure exists but login UI is out of scope)
- Internationalisation
- Offline capability

---

## Navigation Structure

Two top-level sections: **Manage** (data entry) and **Review** (existing oversight workbenches).

```
Manage
├── Trials                           (master table: all trials for tenant)
│   └── [selected trial]
│       ├── Overview                 (summary metrics + activate action)
│       ├── Sites                    (table + add site form)
│       ├── Patients                 (table, drill to patient detail)
│       │   └── [selected patient]
│       │       ├── Enrollment       (status, screening, consent withdrawal)
│       │       ├── Adverse Events   (table + report form)
│       │       ├── Visits           (table + schedule/record form)
│       │       ├── Lab Results      (table + record form)
│       │       ├── Vitals           (table + record form)
│       │       ├── Medications      (table + record form)
│       │       └── Study Drug       (table + record form)
│       ├── Deviations              (table + report form)
│       └── Amendments              (table + propose form)
Review
├── Work Queue
├── Safety Workbench
├── Protocol Workbench
└── Operations
```

Pages DSL: `tree()` for top-level sections, `tabs()` for trial detail and patient detail.

Trial list is the landing page. Row click navigates to trial detail. Patient table row click navigates to patient detail (nested tabs).

Deviations and amendments are at trial level (matching REST hierarchy). Patient-level entities (visits, labs, vitals, meds, drug admin) are scoped under patient detail.

Review section is unchanged — existing workbenches as-is.

---

## Backend: New REST Endpoints

### New endpoints on existing resources

**TrialResource:**
- `GET /trials` — list all trials for current tenant. Returns `List<TrialListRow>` (id, protocolId, phase, sponsor, status, targetEnrollment, enrolledCount). `@RolesAllowed` all roles.

**PatientResource:**
- `GET /trials/{trialId}/sites/{siteId}/patients` — list patients for a site. Returns `List<PatientEnrollment>`. `@RolesAllowed` all roles.
- `GET /trials/{trialId}/sites/{siteId}/patients/{enrollmentId}/adverse-events` — list AEs for a patient (enrollment-scoped). The existing trial-scoped list on `TrialDashboardResource` is insufficient for patient detail tabs. Returns `List<AdverseEvent>`. `@RolesAllowed` all roles.

**ProtocolAmendmentResource:**
- `GET /trials/{trialId}/amendments` — list amendments for a trial. Returns `List<AmendmentResponse>`. `@RolesAllowed` all roles.

**Note on patient list scope:** The UI Patients tab at trial level uses the existing trial-scoped `GET /trials/{id}/patients` from `TrialDashboardResource`. The new site-scoped `GET /trials/{trialId}/sites/{siteId}/patients` supports the enrollment form (which needs to know which site) and the new patient-scoped resources (which have siteId in their path).

### New REST resources

All follow existing patterns: `@RolesAllowed({INVESTIGATOR, COORDINATOR})` for writes, all roles for reads. `findByIdForTenant` for tenant isolation. `@Valid` on request bodies.

**VisitResource** — `@Path("/trials/{trialId}/sites/{siteId}/patients/{enrollmentId}/visits")`

| Method | Path | Body | Description |
|--------|------|------|-------------|
| POST | `/` | `ScheduleVisitRequest` | Schedule/record a visit |
| GET | `/` | — | List visits for patient |
| GET | `/{visitId}` | — | Get visit detail |
| PATCH | `/{visitId}` | `UpdateVisitRequest` | Update status/notes |

**LabResultResource** — `@Path("/trials/{trialId}/sites/{siteId}/patients/{enrollmentId}/lab-results")`

| Method | Path | Body | Description |
|--------|------|------|-------------|
| POST | `/` | `RecordLabResultRequest` | Record lab result |
| GET | `/` | — | List lab results for patient |
| GET | `/{labId}` | — | Get lab detail |

**VitalSignResource** — `@Path("/trials/{trialId}/sites/{siteId}/patients/{enrollmentId}/vitals")`

| Method | Path | Body | Description |
|--------|------|------|-------------|
| POST | `/` | `RecordVitalSignRequest` | Record vital sign |
| GET | `/` | — | List vitals for patient |
| GET | `/{vitalId}` | — | Get vital detail |

**ConcomitantMedicationResource** — `@Path("/trials/{trialId}/sites/{siteId}/patients/{enrollmentId}/medications")`

| Method | Path | Body | Description |
|--------|------|------|-------------|
| POST | `/` | `RecordMedicationRequest` | Record medication |
| GET | `/` | — | List medications for patient |
| GET | `/{medId}` | — | Get medication detail |
| PATCH | `/{medId}` | `UpdateMedicationRequest` | Update (e.g., discontinue) |

**StudyDrugResource** — `@Path("/trials/{trialId}/sites/{siteId}/patients/{enrollmentId}/study-drug")`

| Method | Path | Body | Description |
|--------|------|------|-------------|
| POST | `/` | `RecordDrugAdminRequest` | Record administration |
| GET | `/` | — | List administrations for patient |
| GET | `/{adminId}` | — | Get administration detail |

---

## Backend: New Domain Entities

Five new Panache Active Record entities in `io.casehub.clinical.entity`. All follow the existing pattern: UUID id, tenantId, `findByIdForTenant` static method, `PanacheEntityBase`.

### Visit

| Field | Type | Constraints |
|-------|------|-------------|
| id | UUID | PK |
| tenantId | String | not null |
| enrollmentId | UUID | FK → PatientEnrollment, not null |
| visitType | VisitType | not null |
| visitDate | Instant | not null |
| status | VisitStatus | not null |
| notes | String | nullable, max 2000 |
| createdAt | Instant | not null, auto |

### LabResult

| Field | Type | Constraints |
|-------|------|-------------|
| id | UUID | PK |
| tenantId | String | not null |
| enrollmentId | UUID | FK → PatientEnrollment, not null |
| visitId | UUID | FK → Visit, nullable |
| testName | String | not null |
| value | BigDecimal | not null |
| unit | String | not null |
| referenceRangeLow | BigDecimal | nullable |
| referenceRangeHigh | BigDecimal | nullable |
| abnormalFlag | AbnormalFlag | not null |
| specimenType | SpecimenType | not null |
| performingLab | String | nullable |
| collectedAt | Instant | not null |
| createdAt | Instant | not null, auto |

### VitalSign

| Field | Type | Constraints |
|-------|------|-------------|
| id | UUID | PK |
| tenantId | String | not null |
| enrollmentId | UUID | FK → PatientEnrollment, not null |
| visitId | UUID | FK → Visit, nullable |
| type | VitalType | not null |
| value | BigDecimal | not null |
| unit | String | not null |
| measuredAt | Instant | not null |
| createdAt | Instant | not null, auto |

### ConcomitantMedication

| Field | Type | Constraints |
|-------|------|-------------|
| id | UUID | PK |
| tenantId | String | not null |
| enrollmentId | UUID | FK → PatientEnrollment, not null |
| medicationName | String | not null |
| indication | String | nullable |
| dose | String | not null |
| unit | String | not null |
| route | MedicationRoute | not null |
| frequency | MedicationFrequency | not null |
| startDate | LocalDate | not null |
| endDate | LocalDate | nullable |
| ongoing | boolean | not null, default true |
| createdAt | Instant | not null, auto |

### StudyDrugAdministration

| Field | Type | Constraints |
|-------|------|-------------|
| id | UUID | PK |
| tenantId | String | not null |
| enrollmentId | UUID | FK → PatientEnrollment, not null |
| visitId | UUID | FK → Visit, nullable |
| drugName | String | not null |
| dose | String | not null |
| unit | String | not null |
| route | MedicationRoute | not null (shared enum) |
| administeredAt | Instant | not null |
| administeredBy | String | not null |
| batchNumber | String | nullable |
| status | DrugAdminStatus | not null |
| createdAt | Instant | not null, auto |

### New Enums

All in `io.casehub.clinical.api.model`:

- `VisitType` — SCREENING, BASELINE, FOLLOW_UP, UNSCHEDULED, END_OF_STUDY
- `VisitStatus` — SCHEDULED, COMPLETED, MISSED, CANCELLED
- `AbnormalFlag` — NORMAL, LOW, HIGH, CRITICAL_LOW, CRITICAL_HIGH
- `SpecimenType` — BLOOD, URINE, CSF, TISSUE
- `VitalType` — HEART_RATE, BP_SYSTOLIC, BP_DIASTOLIC, TEMPERATURE, RESPIRATORY_RATE, O2_SATURATION, WEIGHT, HEIGHT
- `MedicationRoute` — ORAL, IV, IM, SC, TOPICAL, INHALED
- `MedicationFrequency` — ONCE_DAILY, TWICE_DAILY, THREE_TIMES_DAILY, FOUR_TIMES_DAILY, AS_NEEDED, WEEKLY
- `DrugAdminStatus` — ADMINISTERED, HELD, DISCONTINUED, DOSE_MODIFIED

### Flyway Migrations

**Default datasource** (`db/migration/default/`):
- V124 — `visit` table
- V125 — `lab_result` table
- V126 — `vital_sign` table
- V127 — `concomitant_medication` table
- V128 — `study_drug_administration` table

**Qhorus datasource** (`db/migration/qhorus/`):
- V2008 — `visit_ledger_entry` join table
- V2009 — `lab_result_ledger_entry` join table
- V2010 — `vital_sign_ledger_entry` join table
- V2011 — `concomitant_medication_ledger_entry` join table
- V2012 — `study_drug_administration_ledger_entry` join table

### LedgerEntry Subclasses

Five new classes in `io.casehub.clinical.ledger`, following the existing pattern (extend `LedgerEntry`, override `domainContentBytes()`):

- `VisitLedgerEntry` — records visit scheduling and status changes
- `LabResultLedgerEntry` — records lab result capture
- `VitalSignLedgerEntry` — records vital sign capture
- `ConcomitantMedicationLedgerEntry` — records medication start/stop
- `StudyDrugLedgerEntry` — records drug administration

Each has a corresponding writer service in `io.casehub.clinical.service` that creates the entry, attaches `ClinicalComplianceSupplement`, and saves via `LedgerEntryRepository`.

---

## Frontend: Pages & Forms

All forms use pages DSL with `restSource` / `mutableRestSource`. Custom Lit components only where pages DSL genuinely cannot handle the interaction.

### Demo mode removal

Remove `DEMO_MODE` flag, `dualDataset()` helper, all CSV mock files, and the `mock/` directory. All datasets bind directly to REST endpoints. The scenario tool handles demo automation through real form submissions.

### Manage → Trials (landing page)

**Table:** all trials via `GET /trials`
**Columns:** Protocol ID, Phase, Sponsor, Status, Enrolled/Target
**Form ("New Trial"):** protocolId (text, required), phase (dropdown: TrialPhase), sponsor (text, required), targetEnrollment (number, min 1), sponsorNotificationConnectorId (text, optional), sponsorNotificationDestination (text, optional)

### Trial Detail → Overview

**Metrics row:** phase, total enrolled, AE count, deviation count (existing trial-summary dataset)
**Actions:** "Activate Trial" button (POST, confirm dialog, disabled when status ≠ PLANNING)
**Sponsor config form:** connectorId (text), destination (text) — PATCH

### Trial Detail → Sites

**Table:** sites via `GET /trials/{id}/sites`
**Columns:** Site ID, Investigator, Target Enrollment, Status, Enrolled Count, AE Count
**Form ("Add Site"):** investigatorId (text, required), targetEnrollment (number, required, min 1)

### Trial Detail → Patients

**Table:** patients via `GET /trials/{id}/patients`
**Columns:** Patient ID, Site, Status, Consent, Enrolled Date
**Form ("Enroll Patient"):** site (dropdown, populated from trial's sites list, required — resolves the siteId path parameter), patientId (text, required) — POST to `/trials/{trialId}/sites/{siteId}/patients`
**Row click:** navigates to Patient Detail

### Patient Detail → Enrollment

**Read-only card:** enrollment status, consent status, screening result
**Screen Patient form:** dynamic list of criterion results — each row: criterionName (text) + met (checkbox). This likely needs a custom blocks-ui component for the dynamic list.
**Action:** "Withdraw Consent" button (POST, confirm dialog, disabled when already withdrawn)

### Patient Detail → Adverse Events

**Table:** AEs via `GET .../adverse-events`
**Columns:** Grade, Event Type, Occurred, SLA Remaining, Escalation Status
**Form ("Report AE"):** grade (dropdown: CtcaeGrade), occurredAt (date picker), actuality (dropdown: ACTUAL/POTENTIAL), unexpected (checkbox), suspected (checkbox)
**Existing regrade component:** unchanged

### Patient Detail → Visits

**Table:** visits via `GET .../visits`
**Columns:** Visit Type, Date, Status, Notes
**Form ("Schedule Visit"):** visitType (dropdown), visitDate (date picker), status (dropdown), notes (textarea, optional)
**Inline update:** status change via PATCH (dropdown in table or detail panel)

### Patient Detail → Lab Results

**Table:** labs via `GET .../lab-results`
**Columns:** Test Name, Value, Unit, Range, Flag, Specimen, Collected
**Form ("Record Lab"):** testName (text), value (number), unit (text), referenceRangeLow (number, optional), referenceRangeHigh (number, optional), abnormalFlag (dropdown), specimenType (dropdown), performingLab (text, optional), collectedAt (date picker), visitId (dropdown of patient's visits, optional)

### Patient Detail → Vitals

**Table:** vitals via `GET .../vitals`
**Columns:** Type, Value, Unit, Measured At
**Form ("Record Vital"):** type (dropdown: VitalType), value (number), unit (text), measuredAt (date picker), visitId (dropdown, optional)

### Patient Detail → Medications

**Table:** meds via `GET .../medications`
**Columns:** Name, Dose, Route, Frequency, Start, End, Ongoing
**Form ("Record Medication"):** medicationName (text), indication (text, optional), dose (text), unit (text), route (dropdown), frequency (dropdown), startDate (date), endDate (date, optional), ongoing (checkbox)
**Inline update:** discontinue via PATCH (set endDate, clear ongoing)

### Patient Detail → Study Drug

**Table:** administrations via `GET .../study-drug`
**Columns:** Drug, Dose, Route, Administered At, By, Batch, Status
**Form ("Record Administration"):** drugName (text), dose (text), unit (text), route (dropdown), administeredAt (date picker), administeredBy (text), batchNumber (text, optional), status (dropdown)

### Trial Detail → Deviations

**Table:** deviations via existing `GET /trials/{id}/deviations`
**Columns:** Type, Severity, Site, PI Approval, IRB Decision, Reported
**Form ("Report Deviation"):** site (dropdown, populated from trial's sites list, required — resolves the siteId path parameter), deviationType (text, required), severity (dropdown: MINOR/MAJOR/CRITICAL) — POST to `/trials/{trialId}/sites/{siteId}/deviations`

### Trial Detail → Amendments

**Table:** amendments via `GET /trials/{id}/amendments` (new endpoint)
**Columns:** Proposed Change (truncated), Status, Created
**Form ("Propose Amendment"):** proposedChange (textarea, required)

### Review Section

Unchanged. Work Queue, Safety Workbench, Protocol Workbench, Operations remain as-is.

### URL parameter propagation

The master-detail flow (trial list → trial detail → patient detail) requires `trialId`, `siteId`, and `enrollmentId` to propagate from parent selection into child tab endpoint URLs. Implementation approach: use pages `selection` events and programmatic dataset URL construction. When a trial row is selected, store trialId in app state and rebuild child datasets with the selected ID interpolated into URLs. Same pattern for patient selection → patient detail tabs. This replaces the current hardcoded `TRIAL_ID` constant.

### Deferred: critical lab safety pipeline integration

Lab results with `CRITICAL_LOW` or `CRITICAL_HIGH` abnormal flags are clinically analogous to serious adverse events and should eventually trigger safety alerts (DSMB notification, SLA tracking). This is explicitly deferred from this spec — the current scope is data capture and forms. A follow-up issue should wire critical lab results into `TrialSafetySignalService` alongside the existing AE safety pipeline.

---

## Testing Strategy

### Unit tests
- Each new enum: value coverage
- Each new LedgerEntry subclass: `domainContentBytes()` round-trip
- Each new writer service: Mockito-mocked `LedgerEntryRepository`

### Integration tests (`@QuarkusTest`)
- Each new REST resource: POST creates entity, GET retrieves it, list returns collection
- Tenant isolation: entity created by tenant A not visible to tenant B
- Validation: missing required fields return 400
- PATCH operations: status transitions, discontinuation
- Ledger integration: write operation creates corresponding ledger entry

### Existing test stability
- New entities added to `quarkus.hibernate-orm.packages` in test `application.properties`
- New migrations in `db/migration/default/` (tests use drop-and-create, so no migration conflicts)
- New ledger join tables in `db/migration/qhorus/` (same drop-and-create pattern)

---

## Build Order

Implementation should proceed in layers to keep the build green at each step:

1. **Enums** — all 8 new enums in `api/` module
2. **Entities** — 5 new Panache entities in `runtime/`, Flyway migrations, test `application.properties` updates
3. **LedgerEntry subclasses** — 5 subclasses + writer services, qhorus migrations
4. **REST resources** — 5 new resources + 4 new endpoints on existing resources (including patient-scoped AE list), with integration tests
5. **Frontend: navigation restructure** — replace flat tree with two-section layout, remove demo mode
6. **Frontend: trial list + detail** — landing page, trial creation form, overview tab
7. **Frontend: existing entity forms** — sites, patients, AEs, deviations, amendments
8. **Frontend: new entity forms** — visits, labs, vitals, medications, study drug

Each step is independently testable and committable.
