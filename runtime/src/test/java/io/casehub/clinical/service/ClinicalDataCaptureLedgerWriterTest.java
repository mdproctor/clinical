package io.casehub.clinical.service;

import io.casehub.clinical.api.model.*;
import io.casehub.clinical.entity.*;
import io.casehub.clinical.ledger.*;
import io.casehub.ledger.api.spi.LedgerEntryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClinicalDataCaptureLedgerWriterTest {

    @Mock LedgerEntryRepository ledgerEntryRepository;
    @Mock Clock clock;

    @InjectMocks VisitLedgerWriter visitWriter;
    @InjectMocks LabResultLedgerWriter labWriter;
    @InjectMocks VitalSignLedgerWriter vitalWriter;
    @InjectMocks ConcomitantMedicationLedgerWriter medWriter;
    @InjectMocks StudyDrugLedgerWriter drugWriter;

    private void setupClock() {
        when(clock.instant()).thenReturn(Instant.parse("2026-08-15T10:00:00Z"));
        when(ledgerEntryRepository.findLatestBySubjectId(any(), eq("default"))).thenReturn(Optional.empty());
    }

    @Test
    void visitWriter_creates_entry_with_correct_fields() {
        setupClock();
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

    @Test
    void labWriter_creates_entry_with_correct_fields() {
        setupClock();
        LabResult lr = new LabResult();
        lr.id = UUID.randomUUID();
        lr.enrollmentId = UUID.randomUUID();
        lr.testName = "ALT";
        lr.value = new BigDecimal("45.5");
        lr.unit = "U/L";
        lr.abnormalFlag = AbnormalFlag.CRITICAL_HIGH;
        lr.specimenType = SpecimenType.BLOOD;

        labWriter.writeEntry(lr);

        ArgumentCaptor<LabResultLedgerEntry> captor = ArgumentCaptor.forClass(LabResultLedgerEntry.class);
        verify(ledgerEntryRepository).save(captor.capture(), eq("default"));
        LabResultLedgerEntry entry = captor.getValue();
        assertEquals(lr.id, entry.labResultId);
        assertEquals("ALT", entry.testName);
        assertEquals(new BigDecimal("45.5"), entry.resultValue);
        assertEquals("CRITICAL_HIGH", entry.abnormalFlag);
    }

    @Test
    void vitalWriter_creates_entry_with_correct_fields() {
        setupClock();
        VitalSign vs = new VitalSign();
        vs.id = UUID.randomUUID();
        vs.enrollmentId = UUID.randomUUID();
        vs.type = VitalType.BP_SYSTOLIC;
        vs.value = new BigDecimal("120");
        vs.unit = "mmHg";

        vitalWriter.writeEntry(vs);

        ArgumentCaptor<VitalSignLedgerEntry> captor = ArgumentCaptor.forClass(VitalSignLedgerEntry.class);
        verify(ledgerEntryRepository).save(captor.capture(), eq("default"));
        VitalSignLedgerEntry entry = captor.getValue();
        assertEquals(vs.id, entry.vitalSignId);
        assertEquals("BP_SYSTOLIC", entry.vitalType);
        assertEquals(new BigDecimal("120"), entry.resultValue);
    }

    @Test
    void medWriter_creates_entry_with_correct_fields() {
        setupClock();
        ConcomitantMedication cm = new ConcomitantMedication();
        cm.id = UUID.randomUUID();
        cm.enrollmentId = UUID.randomUUID();
        cm.medicationName = "Metformin";
        cm.dose = "500";
        cm.unit = "mg";
        cm.route = MedicationRoute.ORAL;
        cm.frequency = MedicationFrequency.TWICE_DAILY;
        cm.startDate = LocalDate.of(2026, 8, 15);

        medWriter.writeEntry(cm);

        ArgumentCaptor<ConcomitantMedicationLedgerEntry> captor = ArgumentCaptor.forClass(ConcomitantMedicationLedgerEntry.class);
        verify(ledgerEntryRepository).save(captor.capture(), eq("default"));
        ConcomitantMedicationLedgerEntry entry = captor.getValue();
        assertEquals(cm.id, entry.medicationId);
        assertEquals("Metformin", entry.medicationName);
        assertEquals("ORAL", entry.route);
        assertEquals(LocalDate.of(2026, 8, 15), entry.startDate);
    }

    @Test
    void drugWriter_creates_entry_with_correct_fields() {
        setupClock();
        StudyDrugAdministration sda = new StudyDrugAdministration();
        sda.id = UUID.randomUUID();
        sda.enrollmentId = UUID.randomUUID();
        sda.drugName = "Pembrolizumab";
        sda.dose = "200";
        sda.unit = "mg";
        sda.route = MedicationRoute.IV;
        sda.administeredBy = "nurse-001";
        sda.status = DrugAdminStatus.ADMINISTERED;

        drugWriter.writeEntry(sda);

        ArgumentCaptor<StudyDrugLedgerEntry> captor = ArgumentCaptor.forClass(StudyDrugLedgerEntry.class);
        verify(ledgerEntryRepository).save(captor.capture(), eq("default"));
        StudyDrugLedgerEntry entry = captor.getValue();
        assertEquals(sda.id, entry.drugAdminId);
        assertEquals("Pembrolizumab", entry.drugName);
        assertEquals("IV", entry.route);
        assertEquals("ADMINISTERED", entry.drugStatus);
    }

    @Test
    void sequenceNumber_increments_from_latest() {
        when(clock.instant()).thenReturn(Instant.parse("2026-08-15T10:00:00Z"));
        VisitLedgerEntry existing = new VisitLedgerEntry();
        existing.sequenceNumber = 3;
        when(ledgerEntryRepository.findLatestBySubjectId(any(), eq("default"))).thenReturn(Optional.of(existing));

        Visit v = new Visit();
        v.id = UUID.randomUUID();
        v.enrollmentId = UUID.randomUUID();
        v.visitType = VisitType.FOLLOW_UP;
        v.visitDate = Instant.now();
        v.status = VisitStatus.SCHEDULED;

        visitWriter.writeEntry(v);

        ArgumentCaptor<VisitLedgerEntry> captor = ArgumentCaptor.forClass(VisitLedgerEntry.class);
        verify(ledgerEntryRepository).save(captor.capture(), eq("default"));
        assertEquals(4, captor.getValue().sequenceNumber);
    }
}
