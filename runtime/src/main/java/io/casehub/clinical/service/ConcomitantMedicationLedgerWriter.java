package io.casehub.clinical.service;

import io.casehub.clinical.api.ClinicalActors;
import io.casehub.clinical.entity.ConcomitantMedication;
import io.casehub.clinical.ledger.ConcomitantMedicationLedgerEntry;
import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.api.spi.LedgerEntryRepository;
import io.casehub.platform.api.identity.ActorType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Clock;
import java.util.UUID;

@ApplicationScoped
public class ConcomitantMedicationLedgerWriter {

    @Inject LedgerEntryRepository ledgerEntryRepository;
    @Inject Clock clock;

    public void writeEntry(ConcomitantMedication med) {
        ConcomitantMedicationLedgerEntry entry = new ConcomitantMedicationLedgerEntry();
        entry.id = UUID.randomUUID();
        entry.subjectId = med.id;
        entry.sequenceNumber = nextSequenceNumber(med.id);
        entry.entryType = LedgerEntryType.EVENT;
        entry.actorId = ClinicalActors.CLINICAL_SERVICE;
        entry.actorType = ActorType.SYSTEM;
        entry.actorRole = "ClinicalDataCapture";
        entry.occurredAt = clock.instant();
        entry.medicationId = med.id;
        entry.enrollmentId = med.enrollmentId;
        entry.medicationName = med.medicationName;
        entry.dose = med.dose;
        entry.unit = med.unit;
        entry.route = med.route.name();
        entry.frequency = med.frequency.name();
        entry.startDate = med.startDate;
        entry.attach(ClinicalComplianceSupplement.dataCapture());
        ledgerEntryRepository.save(entry, "default");
    }

    private int nextSequenceNumber(UUID subjectId) {
        return ledgerEntryRepository.findLatestBySubjectId(subjectId, "default")
                .map(e -> e.sequenceNumber + 1).orElse(1);
    }
}
