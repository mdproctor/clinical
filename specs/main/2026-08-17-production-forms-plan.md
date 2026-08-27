# Production Forms & Clinical Data Capture — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** TBD — create before starting implementation
**Issue group:** TBD

**Goal:** Add production data-entry forms and five new clinical entities to transform casehub-clinical into a deployable reference architecture.

**Architecture:** Backend-first layered build. Eight new enums, five new Panache entities with Flyway migrations, five LedgerEntry subclasses with writer services, five new REST resources plus four new endpoints on existing resources. Frontend restructured from flat four-page layout to two-section Manage/Review navigation with pages DSL forms consuming mutableRestSource.

**Tech Stack:** Java 21 / Quarkus 3.32.2, Panache Active Record, Flyway, H2 (test), casehub-ledger, casehub-pages TypeScript DSL, blocks-ui

## Global Constraints

- Migration versions: default datasource V130+, qhorus datasource V2033+
- Enums in `api/src/main/java/io/casehub/clinical/api/model/`
- Entities in `runtime/src/main/java/io/casehub/clinical/entity/`
- LedgerEntry subclasses in `runtime/src/main/java/io/casehub/clinical/ledger/`
- REST resources in `runtime/src/main/java/io/casehub/clinical/resource/`
- Writer services in `runtime/src/main/java/io/casehub/clinical/service/`
- Tests use `drop-and-create` — no Flyway conflict risk in tests
- `quarkus.hibernate-orm.packages` already includes `io.casehub.clinical.entity` and `io.casehub.clinical.ledger` — no config change needed
- All entities follow: UUID id, tenantId, `findByIdForTenant(UUID, CurrentPrincipal)` static method, `PanacheEntityBase`, `@DynamicUpdate`
- All ledger entries follow: extend `JpaLedgerEntry`, `@DiscriminatorValue`, override `domainContentBytes()`, FK to base `ledger_entry(id)`
- All REST resources follow: `@RolesAllowed({INVESTIGATOR, COORDINATOR})` for writes, all 4 roles for reads, `@Valid` on request bodies
- All tests follow: `@QuarkusTest`, `@TestSecurity(user = "test-actor", roles = {...})`, `@Inject FixedCurrentPrincipal`, RestAssured
- Webui source in `runtime/src/main/webui/src/`
- `MedicationRoute` enum is shared between ConcomitantMedication and StudyDrugAdministration

---

## Batch 1: Domain model — enums, entities, migrations

All foundation types. After this batch: new tables exist, project compiles, existing tests pass.

### Task 1: New enums

**Files:**
- Create: `api/src/main/java/io/casehub/clinical/api/model/VisitType.java`
- Create: `api/src/main/java/io/casehub/clinical/api/model/VisitStatus.java`
- Create: `api/src/main/java/io/casehub/clinical/api/model/AbnormalFlag.java`
- Create: `api/src/main/java/io/casehub/clinical/api/model/SpecimenType.java`
- Create: `api/src/main/java/io/casehub/clinical/api/model/VitalType.java`
- Create: `api/src/main/java/io/casehub/clinical/api/model/MedicationRoute.java`
- Create: `api/src/main/java/io/casehub/clinical/api/model/MedicationFrequency.java`
- Create: `api/src/main/java/io/casehub/clinical/api/model/DrugAdminStatus.java`
- Test: `api/src/test/java/io/casehub/clinical/api/model/ClinicalEnumValuesTest.java`

**Interfaces:**
- Produces: 8 enum types consumed by all subsequent tasks

- [ ] **Step 1: Write test for all enum value counts**

```java
package io.casehub.clinical.api.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ClinicalEnumValuesTest {
    @Test void visitType_has_5_values() { assertEquals(5, VisitType.values().length); }
    @Test void visitStatus_has_4_values() { assertEquals(4, VisitStatus.values().length); }
    @Test void abnormalFlag_has_5_values() { assertEquals(5, AbnormalFlag.values().length); }
    @Test void specimenType_has_4_values() { assertEquals(4, SpecimenType.values().length); }
    @Test void vitalType_has_8_values() { assertEquals(8, VitalType.values().length); }
    @Test void medicationRoute_has_6_values() { assertEquals(6, MedicationRoute.values().length); }
    @Test void medicationFrequency_has_6_values() { assertEquals(6, MedicationFrequency.values().length); }
    @Test void drugAdminStatus_has_4_values() { assertEquals(4, DrugAdminStatus.values().length); }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -pl api -Dtest=ClinicalEnumValuesTest --batch-mode`
Expected: compilation failure (enums don't exist)

- [ ] **Step 3: Create all 8 enums**

Use `ide_create_file` for each:

```java
// VisitType.java
package io.casehub.clinical.api.model;
public enum VisitType { SCREENING, BASELINE, FOLLOW_UP, UNSCHEDULED, END_OF_STUDY }

// VisitStatus.java
package io.casehub.clinical.api.model;
public enum VisitStatus { SCHEDULED, COMPLETED, MISSED, CANCELLED }

// AbnormalFlag.java
package io.casehub.clinical.api.model;
public enum AbnormalFlag { NORMAL, LOW, HIGH, CRITICAL_LOW, CRITICAL_HIGH }

// SpecimenType.java
package io.casehub.clinical.api.model;
public enum SpecimenType { BLOOD, URINE, CSF, TISSUE }

// VitalType.java
package io.casehub.clinical.api.model;
public enum VitalType {
    HEART_RATE, BP_SYSTOLIC, BP_DIASTOLIC, TEMPERATURE,
    RESPIRATORY_RATE, O2_SATURATION, WEIGHT, HEIGHT
}

// MedicationRoute.java
package io.casehub.clinical.api.model;
public enum MedicationRoute { ORAL, IV, IM, SC, TOPICAL, INHALED }

// MedicationFrequency.java
package io.casehub.clinical.api.model;
public enum MedicationFrequency {
    ONCE_DAILY, TWICE_DAILY, THREE_TIMES_DAILY, FOUR_TIMES_DAILY, AS_NEEDED, WEEKLY
}

// DrugAdminStatus.java
package io.casehub.clinical.api.model;
public enum DrugAdminStatus { ADMINISTERED, HELD, DISCONTINUED, DOSE_MODIFIED }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -pl api -Dtest=ClinicalEnumValuesTest --batch-mode`
Expected: PASS — all 8 assertions green

- [ ] **Step 5: Commit**

```bash
git add api/src/main/java/io/casehub/clinical/api/model/VisitType.java api/src/main/java/io/casehub/clinical/api/model/VisitStatus.java api/src/main/java/io/casehub/clinical/api/model/AbnormalFlag.java api/src/main/java/io/casehub/clinical/api/model/SpecimenType.java api/src/main/java/io/casehub/clinical/api/model/VitalType.java api/src/main/java/io/casehub/clinical/api/model/MedicationRoute.java api/src/main/java/io/casehub/clinical/api/model/MedicationFrequency.java api/src/main/java/io/casehub/clinical/api/model/DrugAdminStatus.java api/src/test/java/io/casehub/clinical/api/model/ClinicalEnumValuesTest.java
git commit -m "feat(#N): add 8 clinical data capture enums — visit, lab, vital, medication, drug admin types Refs #N"
```

---

### Task 2: New entities + Flyway migrations

**Files:**
- Create: `runtime/src/main/java/io/casehub/clinical/entity/Visit.java`
- Create: `runtime/src/main/java/io/casehub/clinical/entity/LabResult.java`
- Create: `runtime/src/main/java/io/casehub/clinical/entity/VitalSign.java`
- Create: `runtime/src/main/java/io/casehub/clinical/entity/ConcomitantMedication.java`
- Create: `runtime/src/main/java/io/casehub/clinical/entity/StudyDrugAdministration.java`
- Create: `runtime/src/main/resources/db/migration/default/V130__visit.sql`
- Create: `runtime/src/main/resources/db/migration/default/V131__lab_result.sql`
- Create: `runtime/src/main/resources/db/migration/default/V132__vital_sign.sql`
- Create: `runtime/src/main/resources/db/migration/default/V133__concomitant_medication.sql`
- Create: `runtime/src/main/resources/db/migration/default/V134__study_drug_administration.sql`
- Test: `runtime/src/test/java/io/casehub/clinical/entity/ClinicalEntityPersistenceTest.java`

**Interfaces:**
- Consumes: 8 enums from Task 1
- Produces: 5 Panache entity types consumed by REST resources (Task 5+) and ledger writers (Task 3)

- [ ] **Step 1: Write integration test for Visit entity persistence**

```java
package io.casehub.clinical.entity;

import io.casehub.clinical.api.ClinicalGroups;
import io.casehub.clinical.api.model.*;
import io.casehub.platform.testing.FixedCurrentPrincipal;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@TestSecurity(user = "test-actor", roles = {ClinicalGroups.SPONSOR, ClinicalGroups.INVESTIGATOR, ClinicalGroups.COORDINATOR})
class ClinicalEntityPersistenceTest {

    @Inject FixedCurrentPrincipal principal;

    @Test
    @Transactional
    void visit_persists_and_retrieves() {
        Visit v = new Visit();
        v.id = UUID.randomUUID();
        v.tenantId = principal.tenancyId();
        v.enrollmentId = UUID.randomUUID();
        v.visitType = VisitType.BASELINE;
        v.visitDate = Instant.now();
        v.status = VisitStatus.SCHEDULED;
        v.notes = "Initial screening visit";
        v.createdAt = Instant.now();
        v.persist();

        Visit found = Visit.findByIdForTenant(v.id, principal);
        assertNotNull(found);
        assertEquals(VisitType.BASELINE, found.visitType);
        assertEquals(VisitStatus.SCHEDULED, found.status);
        assertEquals("Initial screening visit", found.notes);
    }

    @Test
    @Transactional
    void labResult_persists_and_retrieves() {
        LabResult lr = new LabResult();
        lr.id = UUID.randomUUID();
        lr.tenantId = principal.tenancyId();
        lr.enrollmentId = UUID.randomUUID();
        lr.testName = "ALT";
        lr.value = new BigDecimal("45.5");
        lr.unit = "U/L";
        lr.referenceRangeLow = new BigDecimal("7.0");
        lr.referenceRangeHigh = new BigDecimal("56.0");
        lr.abnormalFlag = AbnormalFlag.NORMAL;
        lr.specimenType = SpecimenType.BLOOD;
        lr.performingLab = "Central Lab";
        lr.collectedAt = Instant.now();
        lr.createdAt = Instant.now();
        lr.persist();

        LabResult found = LabResult.findByIdForTenant(lr.id, principal);
        assertNotNull(found);
        assertEquals("ALT", found.testName);
        assertEquals(AbnormalFlag.NORMAL, found.abnormalFlag);
    }

    @Test
    @Transactional
    void vitalSign_persists_and_retrieves() {
        VitalSign vs = new VitalSign();
        vs.id = UUID.randomUUID();
        vs.tenantId = principal.tenancyId();
        vs.enrollmentId = UUID.randomUUID();
        vs.type = VitalType.BP_SYSTOLIC;
        vs.value = new BigDecimal("120");
        vs.unit = "mmHg";
        vs.measuredAt = Instant.now();
        vs.createdAt = Instant.now();
        vs.persist();

        VitalSign found = VitalSign.findByIdForTenant(vs.id, principal);
        assertNotNull(found);
        assertEquals(VitalType.BP_SYSTOLIC, found.type);
    }

    @Test
    @Transactional
    void concomitantMedication_persists_and_retrieves() {
        ConcomitantMedication cm = new ConcomitantMedication();
        cm.id = UUID.randomUUID();
        cm.tenantId = principal.tenancyId();
        cm.enrollmentId = UUID.randomUUID();
        cm.medicationName = "Metformin";
        cm.indication = "Type 2 Diabetes";
        cm.dose = "500";
        cm.unit = "mg";
        cm.route = MedicationRoute.ORAL;
        cm.frequency = MedicationFrequency.TWICE_DAILY;
        cm.startDate = LocalDate.now();
        cm.ongoing = true;
        cm.createdAt = Instant.now();
        cm.persist();

        ConcomitantMedication found = ConcomitantMedication.findByIdForTenant(cm.id, principal);
        assertNotNull(found);
        assertEquals("Metformin", found.medicationName);
        assertEquals(MedicationRoute.ORAL, found.route);
        assertTrue(found.ongoing);
    }

    @Test
    @Transactional
    void studyDrugAdministration_persists_and_retrieves() {
        StudyDrugAdministration sda = new StudyDrugAdministration();
        sda.id = UUID.randomUUID();
        sda.tenantId = principal.tenancyId();
        sda.enrollmentId = UUID.randomUUID();
        sda.drugName = "Pembrolizumab";
        sda.dose = "200";
        sda.unit = "mg";
        sda.route = MedicationRoute.IV;
        sda.administeredAt = Instant.now();
        sda.administeredBy = "nurse-001";
        sda.batchNumber = "PEM-2026-0815";
        sda.status = DrugAdminStatus.ADMINISTERED;
        sda.createdAt = Instant.now();
        sda.persist();

        StudyDrugAdministration found = StudyDrugAdministration.findByIdForTenant(sda.id, principal);
        assertNotNull(found);
        assertEquals("Pembrolizumab", found.drugName);
        assertEquals(DrugAdminStatus.ADMINISTERED, found.status);
    }

    @Test
    @Transactional
    void findByIdForTenant_returns_null_for_wrong_tenant() {
        Visit v = new Visit();
        v.id = UUID.randomUUID();
        v.tenantId = principal.tenancyId();
        v.enrollmentId = UUID.randomUUID();
        v.visitType = VisitType.FOLLOW_UP;
        v.visitDate = Instant.now();
        v.status = VisitStatus.SCHEDULED;
        v.createdAt = Instant.now();
        v.persist();

        principal.setTenancyId("other-tenant");
        assertNull(Visit.findByIdForTenant(v.id, principal));
    }
}
```

- [ ] **Step 2: Run test to verify compilation failure**

Run: `mvn install -pl api --batch-mode && mvn test -pl runtime -Dtest=ClinicalEntityPersistenceTest --batch-mode`
Expected: compilation failure (entity classes don't exist)

- [ ] **Step 3: Create Visit entity**

Use `ide_create_file` for `runtime/src/main/java/io/casehub/clinical/entity/Visit.java`:

```java
package io.casehub.clinical.entity;

import io.casehub.clinical.api.model.VisitStatus;
import io.casehub.clinical.api.model.VisitType;
import io.casehub.platform.api.identity.CurrentPrincipal;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import org.hibernate.annotations.DynamicUpdate;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "visit")
@DynamicUpdate
public class Visit extends PanacheEntityBase {

    @Id public UUID id;

    @Column(name = "tenant_id", nullable = false)
    public String tenantId = "default";

    @Column(name = "enrollment_id", nullable = false)
    public UUID enrollmentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "visit_type", nullable = false)
    public VisitType visitType;

    @Column(name = "visit_date", nullable = false)
    public Instant visitDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public VisitStatus status;

    @Column(length = 2000)
    public String notes;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    public static Visit findByIdForTenant(UUID id, CurrentPrincipal principal) {
        Visit v = findById(id);
        if (v == null) return null;
        if (principal.isCrossTenantAdmin()) return v;
        return v.tenantId.equals(principal.tenancyId()) ? v : null;
    }

    public static List<Visit> listByEnrollment(UUID enrollmentId, String tenantId) {
        return list("enrollmentId = ?1 and tenantId = ?2", enrollmentId, tenantId);
    }
}
```

- [ ] **Step 4: Create remaining 4 entities**

Same pattern as Visit. Use `ide_create_file` for each:

**LabResult.java** — fields: id, tenantId, enrollmentId, visitId (UUID nullable), testName (String), value (BigDecimal), unit (String), referenceRangeLow (BigDecimal nullable), referenceRangeHigh (BigDecimal nullable), abnormalFlag (AbnormalFlag), specimenType (SpecimenType), performingLab (String nullable), collectedAt (Instant), createdAt (Instant). Static methods: `findByIdForTenant`, `listByEnrollment`.

**VitalSign.java** — fields: id, tenantId, enrollmentId, visitId (UUID nullable), type (VitalType), value (BigDecimal), unit (String), measuredAt (Instant), createdAt (Instant). Static methods: `findByIdForTenant`, `listByEnrollment`.

**ConcomitantMedication.java** — fields: id, tenantId, enrollmentId, medicationName (String), indication (String nullable), dose (String), unit (String), route (MedicationRoute), frequency (MedicationFrequency), startDate (LocalDate), endDate (LocalDate nullable), ongoing (boolean, default true), createdAt (Instant). Static methods: `findByIdForTenant`, `listByEnrollment`.

**StudyDrugAdministration.java** — fields: id, tenantId, enrollmentId, visitId (UUID nullable), drugName (String), dose (String), unit (String), route (MedicationRoute), administeredAt (Instant), administeredBy (String), batchNumber (String nullable), status (DrugAdminStatus), createdAt (Instant). Static methods: `findByIdForTenant`, `listByEnrollment`.

- [ ] **Step 5: Create Flyway migrations**

Use `ide_create_file` for each. Follow existing migration pattern (no FK to patient_enrollment — existing entities don't FK to parent, they use enrollmentId as a loose reference):

**V130__visit.sql:**
```sql
CREATE TABLE visit (
    id              UUID            NOT NULL,
    tenant_id       VARCHAR(255)    NOT NULL,
    enrollment_id   UUID            NOT NULL,
    visit_type      VARCHAR(50)     NOT NULL,
    visit_date      TIMESTAMP WITH TIME ZONE NOT NULL,
    status          VARCHAR(50)     NOT NULL,
    notes           VARCHAR(2000),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_visit PRIMARY KEY (id)
);
```

**V131__lab_result.sql:**
```sql
CREATE TABLE lab_result (
    id                  UUID            NOT NULL,
    tenant_id           VARCHAR(255)    NOT NULL,
    enrollment_id       UUID            NOT NULL,
    visit_id            UUID,
    test_name           VARCHAR(255)    NOT NULL,
    value               DECIMAL(19,4)   NOT NULL,
    unit                VARCHAR(50)     NOT NULL,
    reference_range_low DECIMAL(19,4),
    reference_range_high DECIMAL(19,4),
    abnormal_flag       VARCHAR(50)     NOT NULL,
    specimen_type       VARCHAR(50)     NOT NULL,
    performing_lab      VARCHAR(255),
    collected_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_lab_result PRIMARY KEY (id)
);
```

**V132__vital_sign.sql:**
```sql
CREATE TABLE vital_sign (
    id              UUID            NOT NULL,
    tenant_id       VARCHAR(255)    NOT NULL,
    enrollment_id   UUID            NOT NULL,
    visit_id        UUID,
    type            VARCHAR(50)     NOT NULL,
    value           DECIMAL(19,4)   NOT NULL,
    unit            VARCHAR(50)     NOT NULL,
    measured_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_vital_sign PRIMARY KEY (id)
);
```

**V133__concomitant_medication.sql:**
```sql
CREATE TABLE concomitant_medication (
    id              UUID            NOT NULL,
    tenant_id       VARCHAR(255)    NOT NULL,
    enrollment_id   UUID            NOT NULL,
    medication_name VARCHAR(255)    NOT NULL,
    indication      VARCHAR(500),
    dose            VARCHAR(100)    NOT NULL,
    unit            VARCHAR(50)     NOT NULL,
    route           VARCHAR(50)     NOT NULL,
    frequency       VARCHAR(50)     NOT NULL,
    start_date      DATE            NOT NULL,
    end_date        DATE,
    ongoing         BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_concomitant_medication PRIMARY KEY (id)
);
```

**V134__study_drug_administration.sql:**
```sql
CREATE TABLE study_drug_administration (
    id              UUID            NOT NULL,
    tenant_id       VARCHAR(255)    NOT NULL,
    enrollment_id   UUID            NOT NULL,
    visit_id        UUID,
    drug_name       VARCHAR(255)    NOT NULL,
    dose            VARCHAR(100)    NOT NULL,
    unit            VARCHAR(50)     NOT NULL,
    route           VARCHAR(50)     NOT NULL,
    administered_at TIMESTAMP WITH TIME ZONE NOT NULL,
    administered_by VARCHAR(255)    NOT NULL,
    batch_number    VARCHAR(255),
    status          VARCHAR(50)     NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_study_drug_administration PRIMARY KEY (id)
);
```

- [ ] **Step 6: Run tests**

Run: `mvn install -pl api --batch-mode && mvn test -pl runtime -Dtest=ClinicalEntityPersistenceTest --batch-mode`
Expected: all 6 tests PASS

- [ ] **Step 7: Run full test suite to verify no regression**

Run: `mvn test --batch-mode`
Expected: all existing tests PASS

- [ ] **Step 8: Commit**

```bash
git add runtime/src/main/java/io/casehub/clinical/entity/Visit.java runtime/src/main/java/io/casehub/clinical/entity/LabResult.java runtime/src/main/java/io/casehub/clinical/entity/VitalSign.java runtime/src/main/java/io/casehub/clinical/entity/ConcomitantMedication.java runtime/src/main/java/io/casehub/clinical/entity/StudyDrugAdministration.java runtime/src/main/resources/db/migration/default/V130__visit.sql runtime/src/main/resources/db/migration/default/V131__lab_result.sql runtime/src/main/resources/db/migration/default/V132__vital_sign.sql runtime/src/main/resources/db/migration/default/V133__concomitant_medication.sql runtime/src/main/resources/db/migration/default/V134__study_drug_administration.sql runtime/src/test/java/io/casehub/clinical/entity/ClinicalEntityPersistenceTest.java
git commit -m "feat(#N): add 5 clinical data capture entities — visit, lab, vital, medication, drug admin Refs #N"
```

---

## Batch 2: Accountability — ledger subclasses + writers

After this batch: all new clinical data writes produce tamper-evident audit entries.

### Task 3: LedgerEntry subclasses + qhorus migrations

**Files:**
- Create: `runtime/src/main/java/io/casehub/clinical/ledger/VisitLedgerEntry.java`
- Create: `runtime/src/main/java/io/casehub/clinical/ledger/LabResultLedgerEntry.java`
- Create: `runtime/src/main/java/io/casehub/clinical/ledger/VitalSignLedgerEntry.java`
- Create: `runtime/src/main/java/io/casehub/clinical/ledger/ConcomitantMedicationLedgerEntry.java`
- Create: `runtime/src/main/java/io/casehub/clinical/ledger/StudyDrugLedgerEntry.java`
- Create: `runtime/src/main/resources/db/migration/qhorus/V2033__visit_ledger_entry.sql`
- Create: `runtime/src/main/resources/db/migration/qhorus/V2034__lab_result_ledger_entry.sql`
- Create: `runtime/src/main/resources/db/migration/qhorus/V2035__vital_sign_ledger_entry.sql`
- Create: `runtime/src/main/resources/db/migration/qhorus/V2036__concomitant_medication_ledger_entry.sql`
- Create: `runtime/src/main/resources/db/migration/qhorus/V2037__study_drug_ledger_entry.sql`
- Test: `runtime/src/test/java/io/casehub/clinical/ledger/ClinicalLedgerEntryDomainContentTest.java`

**Interfaces:**
- Consumes: entity types from Task 2
- Produces: 5 LedgerEntry subclasses consumed by writer services (Task 4)

- [ ] **Step 1: Write unit test for domainContentBytes() on all 5 subclasses**

```java
package io.casehub.clinical.ledger;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ClinicalLedgerEntryDomainContentTest {

    @Test
    void visitLedgerEntry_domainContentBytes_includes_all_fields() {
        VisitLedgerEntry e = new VisitLedgerEntry();
        e.visitId = UUID.randomUUID();
        e.enrollmentId = UUID.randomUUID();
        e.visitType = "BASELINE";
        e.visitDate = Instant.parse("2026-01-15T10:00:00Z");
        e.visitStatus = "COMPLETED";
        byte[] bytes = e.domainContentBytes();
        String content = new String(bytes);
        assertTrue(content.contains(e.visitId.toString()));
        assertTrue(content.contains("BASELINE"));
        assertTrue(content.contains("COMPLETED"));
    }

    @Test
    void labResultLedgerEntry_domainContentBytes_includes_all_fields() {
        LabResultLedgerEntry e = new LabResultLedgerEntry();
        e.labResultId = UUID.randomUUID();
        e.enrollmentId = UUID.randomUUID();
        e.testName = "ALT";
        e.value = new BigDecimal("45.5");
        e.unit = "U/L";
        e.abnormalFlag = "NORMAL";
        e.specimenType = "BLOOD";
        byte[] bytes = e.domainContentBytes();
        String content = new String(bytes);
        assertTrue(content.contains("ALT"));
        assertTrue(content.contains("45.5"));
        assertTrue(content.contains("NORMAL"));
    }

    @Test
    void vitalSignLedgerEntry_domainContentBytes_includes_all_fields() {
        VitalSignLedgerEntry e = new VitalSignLedgerEntry();
        e.vitalSignId = UUID.randomUUID();
        e.enrollmentId = UUID.randomUUID();
        e.vitalType = "BP_SYSTOLIC";
        e.value = new BigDecimal("120");
        e.unit = "mmHg";
        byte[] bytes = e.domainContentBytes();
        String content = new String(bytes);
        assertTrue(content.contains("BP_SYSTOLIC"));
        assertTrue(content.contains("120"));
    }

    @Test
    void concomitantMedicationLedgerEntry_domainContentBytes_includes_all_fields() {
        ConcomitantMedicationLedgerEntry e = new ConcomitantMedicationLedgerEntry();
        e.medicationId = UUID.randomUUID();
        e.enrollmentId = UUID.randomUUID();
        e.medicationName = "Metformin";
        e.dose = "500";
        e.unit = "mg";
        e.route = "ORAL";
        e.frequency = "TWICE_DAILY";
        e.startDate = LocalDate.of(2026, 1, 15);
        byte[] bytes = e.domainContentBytes();
        String content = new String(bytes);
        assertTrue(content.contains("Metformin"));
        assertTrue(content.contains("ORAL"));
    }

    @Test
    void studyDrugLedgerEntry_domainContentBytes_includes_all_fields() {
        StudyDrugLedgerEntry e = new StudyDrugLedgerEntry();
        e.drugAdminId = UUID.randomUUID();
        e.enrollmentId = UUID.randomUUID();
        e.drugName = "Pembrolizumab";
        e.dose = "200";
        e.unit = "mg";
        e.route = "IV";
        e.administeredBy = "nurse-001";
        e.drugStatus = "ADMINISTERED";
        byte[] bytes = e.domainContentBytes();
        String content = new String(bytes);
        assertTrue(content.contains("Pembrolizumab"));
        assertTrue(content.contains("ADMINISTERED"));
    }

    @Test
    void domainContentBytes_handles_null_fields() {
        VisitLedgerEntry e = new VisitLedgerEntry();
        byte[] bytes = e.domainContentBytes();
        assertNotNull(bytes);
        assertTrue(bytes.length > 0);
    }
}
```

- [ ] **Step 2: Run test — verify compilation failure**

Run: `mvn install -pl api --batch-mode && mvn test -pl runtime -Dtest=ClinicalLedgerEntryDomainContentTest --batch-mode`
Expected: compilation failure

- [ ] **Step 3: Create VisitLedgerEntry (reference pattern for all 5)**

Use `ide_create_file`:

```java
package io.casehub.clinical.ledger;

import io.casehub.ledger.runtime.model.jpa.JpaLedgerEntry;
import jakarta.persistence.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "visit_ledger_entry")
@DiscriminatorValue("VISIT")
public class VisitLedgerEntry extends JpaLedgerEntry {

    @Column(name = "visit_id")
    public UUID visitId;

    @Column(name = "enrollment_id")
    public UUID enrollmentId;

    @Column(name = "visit_type")
    public String visitType;

    @Column(name = "visit_date")
    public Instant visitDate;

    @Column(name = "visit_status")
    public String visitStatus;

    @Override
    protected byte[] domainContentBytes() {
        return String.join("|",
                visitId     != null ? visitId.toString()     : "",
                enrollmentId != null ? enrollmentId.toString() : "",
                visitType   != null ? visitType              : "",
                visitDate   != null ? visitDate.toString()   : "",
                visitStatus != null ? visitStatus            : "")
                .getBytes(StandardCharsets.UTF_8);
    }
}
```

- [ ] **Step 4: Create remaining 4 ledger entry subclasses**

Same pattern. Key fields per subclass:

**LabResultLedgerEntry** — `labResultId (UUID)`, `enrollmentId (UUID)`, `testName`, `value (BigDecimal)`, `unit`, `abnormalFlag`, `specimenType`
**VitalSignLedgerEntry** — `vitalSignId (UUID)`, `enrollmentId (UUID)`, `vitalType`, `value (BigDecimal)`, `unit`
**ConcomitantMedicationLedgerEntry** — `medicationId (UUID)`, `enrollmentId (UUID)`, `medicationName`, `dose`, `unit`, `route`, `frequency`, `startDate (LocalDate)`
**StudyDrugLedgerEntry** — `drugAdminId (UUID)`, `enrollmentId (UUID)`, `drugName`, `dose`, `unit`, `route`, `administeredBy`, `drugStatus`

- [ ] **Step 5: Create qhorus migration files**

Same pattern as V2000__ae_ledger_entry.sql. Each has PK = id (UUID), FK to ledger_entry(id), plus domain columns.

**V2033__visit_ledger_entry.sql:**
```sql
CREATE TABLE visit_ledger_entry (
    id              UUID NOT NULL,
    visit_id        UUID NOT NULL,
    enrollment_id   UUID NOT NULL,
    visit_type      VARCHAR(50) NOT NULL,
    visit_date      TIMESTAMP WITH TIME ZONE NOT NULL,
    visit_status    VARCHAR(50) NOT NULL,
    CONSTRAINT pk_visit_ledger_entry PRIMARY KEY (id),
    CONSTRAINT fk_visit_ledger_entry_base FOREIGN KEY (id) REFERENCES ledger_entry(id)
);
```

Follow same pattern for V2034–V2037.

- [ ] **Step 6: Run tests**

Run: `mvn install -pl api --batch-mode && mvn test -pl runtime -Dtest=ClinicalLedgerEntryDomainContentTest --batch-mode`
Expected: all 6 tests PASS

- [ ] **Step 7: Commit**

---

### Task 4: Ledger writer services

**Files:**
- Create: `runtime/src/main/java/io/casehub/clinical/service/VisitLedgerWriter.java`
- Create: `runtime/src/main/java/io/casehub/clinical/service/LabResultLedgerWriter.java`
- Create: `runtime/src/main/java/io/casehub/clinical/service/VitalSignLedgerWriter.java`
- Create: `runtime/src/main/java/io/casehub/clinical/service/ConcomitantMedicationLedgerWriter.java`
- Create: `runtime/src/main/java/io/casehub/clinical/service/StudyDrugLedgerWriter.java`
- Test: `runtime/src/test/java/io/casehub/clinical/service/ClinicalLedgerWriterTest.java`

**Interfaces:**
- Consumes: LedgerEntry subclasses from Task 3, entities from Task 2, `LedgerEntryRepository`, `ClinicalComplianceSupplement`, `Clock`
- Produces: 5 writer services consumed by REST resources (Task 5+)

- [ ] **Step 1: Write unit test for VisitLedgerWriter (Mockito, reference pattern)**

```java
package io.casehub.clinical.service;

import io.casehub.clinical.entity.Visit;
import io.casehub.clinical.api.model.VisitType;
import io.casehub.clinical.api.model.VisitStatus;
import io.casehub.clinical.ledger.VisitLedgerEntry;
import io.casehub.ledger.api.spi.LedgerEntryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClinicalLedgerWriterTest {

    @Mock LedgerEntryRepository ledgerEntryRepository;
    @Mock Clock clock;

    @InjectMocks VisitLedgerWriter visitWriter;
    @InjectMocks LabResultLedgerWriter labWriter;
    @InjectMocks VitalSignLedgerWriter vitalWriter;
    @InjectMocks ConcomitantMedicationLedgerWriter medWriter;
    @InjectMocks StudyDrugLedgerWriter drugWriter;

    @Test
    void visitWriter_creates_entry_with_correct_fields() {
        when(clock.instant()).thenReturn(Instant.parse("2026-08-15T10:00:00Z"));
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        when(ledgerEntryRepository.findLatestBySubjectId(any(), eq("default"))).thenReturn(Optional.empty());

        Visit v = new Visit();
        v.id = UUID.randomUUID();
        v.enrollmentId = UUID.randomUUID();
        v.visitType = VisitType.BASELINE;
        v.visitDate = Instant.parse("2026-08-15T09:00:00Z");
        v.status = VisitStatus.COMPLETED;

        visitWriter.writeEntry(v);

        ArgumentCaptor<VisitLedgerEntry> captor = ArgumentCaptor.forClass(VisitLedgerEntry.class);
        verify(ledgerEntryRepository).save(captor.capture(), eq("default"));
        VisitLedgerEntry entry = captor.getValue();
        assertEquals(v.id, entry.visitId);
        assertEquals(v.enrollmentId, entry.enrollmentId);
        assertEquals("BASELINE", entry.visitType);
        assertEquals("COMPLETED", entry.visitStatus);
        assertEquals(1, entry.sequenceNumber);
    }
}
```

- [ ] **Step 2: Run test — verify compilation failure**

- [ ] **Step 3: Create VisitLedgerWriter (reference pattern)**

```java
package io.casehub.clinical.service;

import io.casehub.clinical.api.ClinicalActors;
import io.casehub.clinical.entity.Visit;
import io.casehub.clinical.ledger.VisitLedgerEntry;
import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.api.spi.LedgerEntryRepository;
import io.casehub.platform.api.identity.ActorType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Clock;
import java.util.UUID;

@ApplicationScoped
public class VisitLedgerWriter {

    @Inject LedgerEntryRepository ledgerEntryRepository;
    @Inject Clock clock;

    public void writeEntry(Visit visit) {
        VisitLedgerEntry entry = new VisitLedgerEntry();
        entry.id = UUID.randomUUID();
        entry.subjectId = visit.id;
        entry.sequenceNumber = nextSequenceNumber(visit.id);
        entry.entryType = LedgerEntryType.EVENT;
        entry.actorId = ClinicalActors.CLINICAL_SERVICE;
        entry.actorType = ActorType.SYSTEM;
        entry.actorRole = "ClinicalDataCapture";
        entry.occurredAt = clock.instant();
        entry.visitId = visit.id;
        entry.enrollmentId = visit.enrollmentId;
        entry.visitType = visit.visitType.name();
        entry.visitDate = visit.visitDate;
        entry.visitStatus = visit.status.name();
        entry.attach(ClinicalComplianceSupplement.dataCapture());
        ledgerEntryRepository.save(entry, "default");
    }

    private int nextSequenceNumber(UUID subjectId) {
        return ledgerEntryRepository.findLatestBySubjectId(subjectId, "default")
            .map(e -> e.sequenceNumber + 1).orElse(1);
    }
}
```

- [ ] **Step 4: Create remaining 4 writer services**

Same pattern. Each accepts the domain entity, creates the ledger entry, populates fields from entity, attaches `ClinicalComplianceSupplement.dataCapture()`, saves.

- [ ] **Step 5: Add `ClinicalComplianceSupplement.dataCapture()` factory method**

Add to existing `ClinicalComplianceSupplement.java` — a new static factory following the existing pattern (`aeEscalation()`, `piAuthorisation()`, etc.):

```java
public static ComplianceSupplement dataCapture() {
    return new ComplianceSupplement(
        "ICH E6(R3) §5.18",
        "Clinical data capture — tamper-evident audit of all data entry operations",
        "MEDIUM"
    );
}
```

- [ ] **Step 6: Run tests**

Run: `mvn install -pl api --batch-mode && mvn test -pl runtime -Dtest=ClinicalLedgerWriterTest --batch-mode`
Expected: PASS

- [ ] **Step 7: Commit**

---

## Batch 3: REST API — new resources + list endpoints

After this batch: complete REST API for all clinical data operations.

### Task 5: List endpoints on existing resources

**Files:**
- Modify: `runtime/src/main/java/io/casehub/clinical/resource/TrialResource.java` — add `GET /trials` list
- Modify: `runtime/src/main/java/io/casehub/clinical/resource/PatientResource.java` — add `GET .../patients` (site-scoped list) and `GET .../adverse-events` (patient-scoped AE list)
- Modify: `runtime/src/main/java/io/casehub/clinical/resource/ProtocolAmendmentResource.java` — add `GET /trials/{id}/amendments` list
- Test: `runtime/src/test/java/io/casehub/clinical/resource/TrialListResourceTest.java`

**Interfaces:**
- Consumes: existing entity types
- Produces: 4 new list endpoints consumed by frontend

- [ ] **Step 1: Write test for GET /trials list endpoint**

```java
package io.casehub.clinical.resource;

import io.casehub.clinical.api.ClinicalGroups;
import io.casehub.platform.testing.FixedCurrentPrincipal;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
@TestSecurity(user = "test-actor", roles = {ClinicalGroups.SPONSOR, ClinicalGroups.INVESTIGATOR, ClinicalGroups.COORDINATOR})
class TrialListResourceTest {

    @Inject FixedCurrentPrincipal principal;
    @AfterEach void resetPrincipal() { principal.reset(); }

    @Test
    void get_trials_returns_list_including_created_trial() {
        given().contentType("application/json")
            .body("{\"protocolId\":\"LIST-TEST-" + UUID.randomUUID() + "\",\"phase\":\"PHASE_III\",\"sponsor\":\"Acme\",\"targetEnrollment\":100}")
            .when().post("/trials").then().statusCode(201);

        given().when().get("/trials")
            .then().statusCode(200)
            .body("size()", greaterThanOrEqualTo(1))
            .body("[0].protocolId", notNullValue())
            .body("[0].phase", notNullValue())
            .body("[0].status", notNullValue());
    }

    @Test
    void get_trials_filters_by_tenant() {
        given().contentType("application/json")
            .body("{\"protocolId\":\"TENANT-" + UUID.randomUUID() + "\",\"phase\":\"PHASE_I\",\"sponsor\":\"T\",\"targetEnrollment\":5}")
            .when().post("/trials").then().statusCode(201);

        principal.setTenancyId("isolated-tenant");
        given().when().get("/trials")
            .then().statusCode(200).body("size()", equalTo(0));
    }
}
```

- [ ] **Step 2: Run test — verify 404/405 (endpoint doesn't exist)**

- [ ] **Step 3: Implement GET /trials on TrialResource**

Add to `TrialResource.java` using `ide_insert_member`:

```java
public record TrialListRow(UUID id, String protocolId, TrialPhase phase, String sponsor,
                           TrialStatus status, int targetEnrollment, long enrolledCount) {}

@GET
@RolesAllowed({ClinicalGroups.SPONSOR, ClinicalGroups.INVESTIGATOR,
               ClinicalGroups.COORDINATOR, ClinicalGroups.MONITOR})
public List<TrialListRow> list() {
    List<ClinicalTrial> trials = ClinicalTrial.list("tenantId", principal.tenancyId());
    return trials.stream().map(t -> new TrialListRow(
        t.id, t.protocolId, t.phase, t.sponsor, t.status, t.targetEnrollment,
        PatientEnrollment.count("tenantId = ?1 and enrollmentStatus != 'CANDIDATE'",
            t.tenantId)
    )).toList();
}
```

- [ ] **Step 4: Implement patient-scoped list and AE list on PatientResource**

Add `GET /` (list patients for site) and `GET /{enrollmentId}/adverse-events` (list AEs for patient).

- [ ] **Step 5: Implement amendment list on ProtocolAmendmentResource**

Add `GET /trials/{trialId}/amendments` returning list of `AmendmentResponse`.

- [ ] **Step 6: Run tests + full suite**

- [ ] **Step 7: Commit**

---

### Task 6: VisitResource + LabResultResource

**Files:**
- Create: `runtime/src/main/java/io/casehub/clinical/resource/VisitResource.java`
- Create: `runtime/src/main/java/io/casehub/clinical/resource/LabResultResource.java`
- Test: `runtime/src/test/java/io/casehub/clinical/resource/VisitResourceTest.java`
- Test: `runtime/src/test/java/io/casehub/clinical/resource/LabResultResourceTest.java`

**Interfaces:**
- Consumes: Visit, LabResult entities (Task 2), VisitLedgerWriter, LabResultLedgerWriter (Task 4)
- Produces: REST endpoints consumed by frontend

- [ ] **Step 1: Write test for VisitResource POST + GET + list + PATCH**

Test pattern follows SiteResourceTest: create trial → create site → enroll patient → POST visit → GET visit → verify fields. Additional tests for PATCH status update, list by enrollment, 404 for wrong tenant.

- [ ] **Step 2: Write test for LabResultResource POST + GET + list**

Same pattern with lab-specific fields.

- [ ] **Step 3: Implement VisitResource**

POST creates Visit entity + calls `visitLedgerWriter.writeEntry()`. PATCH updates status/notes. GET and list follow existing patterns.

- [ ] **Step 4: Implement LabResultResource**

POST creates LabResult entity + calls `labResultLedgerWriter.writeEntry()`. GET and list follow existing patterns.

- [ ] **Step 5: Run tests + full suite**
- [ ] **Step 6: Commit**

---

### Task 7: VitalSignResource + ConcomitantMedicationResource + StudyDrugResource

**Files:**
- Create: `runtime/src/main/java/io/casehub/clinical/resource/VitalSignResource.java`
- Create: `runtime/src/main/java/io/casehub/clinical/resource/ConcomitantMedicationResource.java`
- Create: `runtime/src/main/java/io/casehub/clinical/resource/StudyDrugResource.java`
- Test: `runtime/src/test/java/io/casehub/clinical/resource/VitalSignResourceTest.java`
- Test: `runtime/src/test/java/io/casehub/clinical/resource/ConcomitantMedicationResourceTest.java`
- Test: `runtime/src/test/java/io/casehub/clinical/resource/StudyDrugResourceTest.java`

**Interfaces:**
- Consumes: remaining entities (Task 2), remaining writers (Task 4)
- Produces: REST endpoints consumed by frontend

Same pattern as Task 6. ConcomitantMedicationResource additionally has PATCH for discontinuation (set endDate, clear ongoing).

- [ ] **Step 1-5: TDD cycle for each resource**
- [ ] **Step 6: Run full test suite**
- [ ] **Step 7: Commit**

---

## Batch 4: Frontend — navigation restructure + trial management

After this batch: new two-section navigation with trial list/detail pages, demo mode removed.

### Task 8: Navigation restructure + demo mode removal

**Files:**
- Modify: `runtime/src/main/webui/src/app.ts` — replace flat tree with two-section Manage/Review layout
- Modify: `runtime/src/main/webui/src/datasets.ts` — remove DEMO_MODE, dualDataset, CSV imports; bind directly to REST
- Modify: `runtime/src/main/webui/src/index.ts` — remove DEMO_WORK_ITEMS and demo wiring
- Delete: `runtime/src/main/webui/src/mock/` directory (all CSV files)
- Create: `runtime/src/main/webui/src/views/manage/trial-list.ts`
- Create: `runtime/src/main/webui/src/views/manage/trial-detail.ts`

**Interfaces:**
- Consumes: `GET /trials` (Task 5), existing dashboard endpoints
- Produces: navigation structure consumed by all subsequent frontend tasks

- [ ] **Step 1: Remove demo mode infrastructure**

In `datasets.ts`: remove `DEMO_MODE`, `dualDataset()`, all CSV imports. Replace with direct REST dataset bindings using `restSource()`. Keep `TRIAL_ID` as URL-parameter-driven (read from hash or state).

- [ ] **Step 2: Replace flat tree in app.ts**

```typescript
import { page, tree, tabs } from "@casehubio/pages-ui";

export const app = page("CaseHub Clinical",
  tree(
    ["Manage", page("Manage",
      tabs(
        ["Trials", trialList()],
      )
    )],
    ["Review", page("Review",
      tree(
        ["Work Queue", workQueue()],
        ["Safety Workbench", safetyWorkbench()],
        ["Protocol Workbench", protocolWorkbench()],
        ["Operations", operations()],
      )
    )],
  ),
  { datasets: [...] }
);
```

- [ ] **Step 3: Create trial-list.ts**

Table bound to `GET /trials` with columns for Protocol ID, Phase, Sponsor, Status, Enrolled/Target. "New Trial" schema form with mutableRestSource POST to `/trials`.

- [ ] **Step 4: Remove mock directory**

Delete all CSV files in `runtime/src/main/webui/src/mock/`.

- [ ] **Step 5: Update index.ts**

Remove `DEMO_WORK_ITEMS`, `DEMO_MODE` usage. Keep component registrations and work-item-inbox wiring (now always hits real endpoint).

- [ ] **Step 6: Verify build**

Run: `yarn --cwd runtime/src/main/webui build`
Expected: compiles without errors

- [ ] **Step 7: Commit**

---

### Task 9: Trial detail + patient-level forms

**Files:**
- Create: `runtime/src/main/webui/src/views/manage/trial-overview.ts`
- Create: `runtime/src/main/webui/src/views/manage/sites-tab.ts`
- Create: `runtime/src/main/webui/src/views/manage/patients-tab.ts`
- Create: `runtime/src/main/webui/src/views/manage/patient-detail.ts`
- Create: `runtime/src/main/webui/src/views/manage/ae-tab.ts`
- Create: `runtime/src/main/webui/src/views/manage/visits-tab.ts`
- Create: `runtime/src/main/webui/src/views/manage/labs-tab.ts`
- Create: `runtime/src/main/webui/src/views/manage/vitals-tab.ts`
- Create: `runtime/src/main/webui/src/views/manage/medications-tab.ts`
- Create: `runtime/src/main/webui/src/views/manage/study-drug-tab.ts`
- Create: `runtime/src/main/webui/src/views/manage/deviations-tab.ts`
- Create: `runtime/src/main/webui/src/views/manage/amendments-tab.ts`

**Interfaces:**
- Consumes: all REST endpoints (Tasks 5-7), navigation structure (Task 8)
- Produces: complete Manage section UI

- [ ] **Step 1: Create trial-overview.ts**

Metrics row + "Activate Trial" action button + sponsor config form.

- [ ] **Step 2: Create sites-tab.ts and patients-tab.ts**

Sites: table + "Add Site" form. Patients: table + "Enroll Patient" form with site dropdown.

- [ ] **Step 3: Wire trial detail tabs**

Update trial-list to navigate to trial detail with tabs: Overview, Sites, Patients, Deviations, Amendments. Each tab imports its module.

- [ ] **Step 4: Create patient-detail.ts with nested tabs**

Patient detail with tabs: Enrollment, Adverse Events, Visits, Labs, Vitals, Medications, Study Drug.

- [ ] **Step 5: Create all patient-level form tabs**

Each tab: table bound to patient-scoped endpoint + schema form for creating new records. Use pages DSL `textInput`, `numberInput`, `dropdown`, `datePicker`, `checkbox`, `textarea` components.

- [ ] **Step 6: Create deviations-tab.ts and amendments-tab.ts**

Trial-level tabs with forms.

- [ ] **Step 7: Verify build + manual test**

Run: `yarn --cwd runtime/src/main/webui build`
Start dev server: `mvn quarkus:dev -pl runtime`
Manual verification: navigate through trial list → trial detail → patient detail → each tab.

- [ ] **Step 8: Commit**

---

## References

- [2026-08-17-production-forms-design.md](2026-08-17-production-forms-design.md) — design spec this plan implements
- `runtime/src/main/java/io/casehub/clinical/entity/AdverseEvent.java` — reference entity pattern
- `runtime/src/main/java/io/casehub/clinical/ledger/ProtocolDeviationLedgerEntry.java` — reference ledger entry pattern
- `runtime/src/main/java/io/casehub/clinical/service/AdverseEventLedgerWriter.java` — reference writer pattern
- `runtime/src/test/java/io/casehub/clinical/resource/SiteResourceTest.java` — reference REST test pattern
- `runtime/src/main/webui/src/app.ts` — current frontend entry point
- `runtime/src/main/webui/src/datasets.ts` — current dataset binding approach
- `docs/platform/boundary-rules.md` — platform boundary verification
- `docs/platform/capability-ownership.md` — capability ownership verification
