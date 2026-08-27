package io.casehub.clinical.service;

import io.casehub.clinical.api.ClinicalActors;
import io.casehub.clinical.entity.VitalSign;
import io.casehub.clinical.ledger.VitalSignLedgerEntry;
import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.api.spi.LedgerEntryRepository;
import io.casehub.platform.api.identity.ActorType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Clock;
import java.util.UUID;

@ApplicationScoped
public class VitalSignLedgerWriter {

    @Inject LedgerEntryRepository ledgerEntryRepository;
    @Inject Clock clock;

    public void writeEntry(VitalSign vital) {
        VitalSignLedgerEntry entry = new VitalSignLedgerEntry();
        entry.id = UUID.randomUUID();
        entry.subjectId = vital.id;
        entry.sequenceNumber = nextSequenceNumber(vital.id);
        entry.entryType = LedgerEntryType.EVENT;
        entry.actorId = ClinicalActors.CLINICAL_SERVICE;
        entry.actorType = ActorType.SYSTEM;
        entry.actorRole = "ClinicalDataCapture";
        entry.occurredAt = clock.instant();
        entry.vitalSignId = vital.id;
        entry.enrollmentId = vital.enrollmentId;
        entry.vitalType = vital.type.name();
        entry.resultValue = vital.value;
        entry.unit = vital.unit;
        entry.attach(ClinicalComplianceSupplement.dataCapture());
        ledgerEntryRepository.save(entry, "default");
    }

    private int nextSequenceNumber(UUID subjectId) {
        return ledgerEntryRepository.findLatestBySubjectId(subjectId, "default")
                .map(e -> e.sequenceNumber + 1).orElse(1);
    }
}
