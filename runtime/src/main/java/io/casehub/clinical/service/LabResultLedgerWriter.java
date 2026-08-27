package io.casehub.clinical.service;

import io.casehub.clinical.api.ClinicalActors;
import io.casehub.clinical.entity.LabResult;
import io.casehub.clinical.ledger.LabResultLedgerEntry;
import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.api.spi.LedgerEntryRepository;
import io.casehub.platform.api.identity.ActorType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Clock;
import java.util.UUID;

@ApplicationScoped
public class LabResultLedgerWriter {

    @Inject LedgerEntryRepository ledgerEntryRepository;
    @Inject Clock clock;

    public void writeEntry(LabResult lab) {
        LabResultLedgerEntry entry = new LabResultLedgerEntry();
        entry.id = UUID.randomUUID();
        entry.subjectId = lab.id;
        entry.sequenceNumber = nextSequenceNumber(lab.id);
        entry.entryType = LedgerEntryType.EVENT;
        entry.actorId = ClinicalActors.CLINICAL_SERVICE;
        entry.actorType = ActorType.SYSTEM;
        entry.actorRole = "ClinicalDataCapture";
        entry.occurredAt = clock.instant();
        entry.labResultId = lab.id;
        entry.enrollmentId = lab.enrollmentId;
        entry.testName = lab.testName;
        entry.resultValue = lab.value;
        entry.unit = lab.unit;
        entry.abnormalFlag = lab.abnormalFlag.name();
        entry.specimenType = lab.specimenType.name();
        entry.attach(ClinicalComplianceSupplement.dataCapture());
        ledgerEntryRepository.save(entry, "default");
    }

    private int nextSequenceNumber(UUID subjectId) {
        return ledgerEntryRepository.findLatestBySubjectId(subjectId, "default")
                .map(e -> e.sequenceNumber + 1).orElse(1);
    }
}
