package io.casehub.clinical.service;

import io.casehub.clinical.api.ClinicalActors;
import io.casehub.clinical.entity.StudyDrugAdministration;
import io.casehub.clinical.ledger.StudyDrugLedgerEntry;
import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.api.spi.LedgerEntryRepository;
import io.casehub.platform.api.identity.ActorType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Clock;
import java.util.UUID;

@ApplicationScoped
public class StudyDrugLedgerWriter {

    @Inject LedgerEntryRepository ledgerEntryRepository;
    @Inject Clock clock;

    public void writeEntry(StudyDrugAdministration drug) {
        StudyDrugLedgerEntry entry = new StudyDrugLedgerEntry();
        entry.id = UUID.randomUUID();
        entry.subjectId = drug.id;
        entry.sequenceNumber = nextSequenceNumber(drug.id);
        entry.entryType = LedgerEntryType.EVENT;
        entry.actorId = ClinicalActors.CLINICAL_SERVICE;
        entry.actorType = ActorType.SYSTEM;
        entry.actorRole = "ClinicalDataCapture";
        entry.occurredAt = clock.instant();
        entry.drugAdminId = drug.id;
        entry.enrollmentId = drug.enrollmentId;
        entry.drugName = drug.drugName;
        entry.dose = drug.dose;
        entry.unit = drug.unit;
        entry.route = drug.route.name();
        entry.administeredBy = drug.administeredBy;
        entry.drugStatus = drug.status.name();
        entry.attach(ClinicalComplianceSupplement.dataCapture());
        ledgerEntryRepository.save(entry, "default");
    }

    private int nextSequenceNumber(UUID subjectId) {
        return ledgerEntryRepository.findLatestBySubjectId(subjectId, "default")
                .map(e -> e.sequenceNumber + 1).orElse(1);
    }
}
