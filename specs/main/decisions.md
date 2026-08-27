# Production Forms Design — Decisions

## D1: Navigation structure

**Choice:** Flat tree with two sections — "Manage" (data entry, trial hierarchy) and "Review" (existing workbenches)
**Alternatives:**
- Entity-centric navigation — separate top-level pages per entity type; loses trial-as-container hierarchy
- Workbench-per-activity — merge manage and review; mixes operational roles, larger pages
**Rationale:** Mirrors how clinical trial systems work (trial → site → patient). Keeps data entry and review cleanly separated. Pages tree() + tabs() handles it naturally.
**Trade-offs:** Users must switch sections to go from entering data to reviewing it. Acceptable because the roles are typically different people.
**Exploration:** quick
**Status:** captured

## D2: Multi-trial support

**Choice:** Trial list as landing page with drill-down into specific trial
**Alternatives:**
- Single trial, configurable via env var — simpler but not a credible reference architecture
**Rationale:** A reference architecture must show multi-tenancy and multi-trial support. Requires new GET /trials endpoint.
**Trade-offs:** Need a new list endpoint that doesn't exist today.
**Exploration:** quick
**Status:** captured

## D3: Scope — full clinical data capture

**Choice:** Design all forms in one pass: existing endpoints + new entities (visits, labs, vitals, meds, dosing)
**Alternatives:**
- Two passes — forms for existing endpoints first, new entities second
**Rationale:** One coherent design for the complete reference architecture surface.
**Trade-offs:** Larger design and implementation scope.
**Exploration:** quick
**Status:** captured

## D4: Model depth

**Choice:** FHIR-representative — moderately detailed fields that a clinical evaluator would expect
**Alternatives:**
- Lean/minimal — core fields only, FHIR-named
- Minimal placeholder — bare minimum, fill out later
**Rationale:** Credible reference architecture needs fields a clinical evaluator recognises without modelling every FHIR extension.
**Trade-offs:** More fields per entity means more migration columns, more form fields, more test coverage.
**Exploration:** quick
**Status:** captured

## D5: Audit depth

**Choice:** Full accountability — LedgerEntry subclasses for all new entities
**Alternatives:**
- Selective — only safety-relevant entities (labs, visits) get ledger entries
- Standard DB only — no ledger integration for new entities
**Rationale:** Consistent with the reference architecture story. Every clinical data point is tamper-evident audited.
**Trade-offs:** More LedgerEntry subclasses, more domainContentBytes() implementations, more migration join tables.
**Depends on:** D3 (scope includes new entities)
**Exploration:** quick
**Status:** captured

## D6: Form technology

**Choice:** Pages DSL + blocks-ui as component framework. Custom Lit only where genuinely needed.
**Alternatives:**
- Custom Lit throughout — more control but more code
**Rationale:** Pages has schema forms, mutableRestSource, and action buttons. Most forms are standard CRUD. Consistent, less code.
**Trade-offs:** Constrained to what pages DSL supports for standard forms.
**Exploration:** quick
**Status:** captured

## D7: Demo mode

**Choice:** Real backend always. New scenario tool handles demo automation through real forms.
**Alternatives:**
- Keep dual CSV/REST mode
- Static demo fallback for screenshots
**Rationale:** Scenario tool drives real forms against real endpoints. CSV mock mode becomes obsolete.
**Trade-offs:** Cannot show the UI without a running backend.
**Exploration:** quick
**Status:** captured

## D8: New domain entities

**Choice:** Five new entities — Visit (ScheduledAssessment), LabResult, VitalSign, ConcomitantMedication, StudyDrugAdministration. All belong to PatientEnrollment. Visit optionally links labs, vitals, and drug admin to a point in time. FHIR-representative field depth.
**Alternatives:**
- Fewer entities (e.g., combine vitals + labs into "Observation") — FHIR-like but loses distinct form UX
- More entities (e.g., separate SpecimenCollection) — over-modelled for a reference architecture
**Rationale:** These five cover the core clinical data capture workflow. Anchoring to PatientEnrollment (not Visit) allows independent existence — a concomitant med doesn't require a visit.
**Trade-offs:** Five new Panache entities, five migrations, five REST resources, five LedgerEntry subclasses, five form pages.
**Depends on:** D3 (full scope), D4 (FHIR-representative depth), D5 (full accountability)
**Exploration:** quick
**Status:** captured
