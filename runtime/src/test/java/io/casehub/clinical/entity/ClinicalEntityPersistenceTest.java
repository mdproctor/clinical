package io.casehub.clinical.entity;

import io.casehub.clinical.api.ClinicalGroups;
import io.casehub.clinical.api.model.*;
import io.casehub.platform.testing.FixedCurrentPrincipal;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.AfterEach;
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

    @AfterEach
    void resetPrincipal() { principal.reset(); }

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
        assertEquals(new BigDecimal("45.5"), found.value);
        assertEquals(AbnormalFlag.NORMAL, found.abnormalFlag);
        assertEquals(SpecimenType.BLOOD, found.specimenType);
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
        assertEquals(new BigDecimal("120"), found.value);
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
        assertEquals(MedicationFrequency.TWICE_DAILY, found.frequency);
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
        assertEquals(MedicationRoute.IV, found.route);
        assertEquals(DrugAdminStatus.ADMINISTERED, found.status);
        assertEquals("PEM-2026-0815", found.batchNumber);
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

    @Test
    @Transactional
    void listByEnrollment_returns_matching_entities() {
        UUID enrollmentId = UUID.randomUUID();
        String tenantId = principal.tenancyId();

        Visit v1 = new Visit();
        v1.id = UUID.randomUUID();
        v1.tenantId = tenantId;
        v1.enrollmentId = enrollmentId;
        v1.visitType = VisitType.SCREENING;
        v1.visitDate = Instant.now();
        v1.status = VisitStatus.COMPLETED;
        v1.createdAt = Instant.now();
        v1.persist();

        Visit v2 = new Visit();
        v2.id = UUID.randomUUID();
        v2.tenantId = tenantId;
        v2.enrollmentId = enrollmentId;
        v2.visitType = VisitType.BASELINE;
        v2.visitDate = Instant.now();
        v2.status = VisitStatus.SCHEDULED;
        v2.createdAt = Instant.now();
        v2.persist();

        Visit other = new Visit();
        other.id = UUID.randomUUID();
        other.tenantId = tenantId;
        other.enrollmentId = UUID.randomUUID();
        other.visitType = VisitType.FOLLOW_UP;
        other.visitDate = Instant.now();
        other.status = VisitStatus.SCHEDULED;
        other.createdAt = Instant.now();
        other.persist();

        assertEquals(2, Visit.listByEnrollment(enrollmentId, tenantId).size());
    }
}
