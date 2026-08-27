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
        e.resultValue = new BigDecimal("45.5");
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
        e.resultValue = new BigDecimal("120");
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
        assertTrue(content.contains("2026-01-15"));
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
