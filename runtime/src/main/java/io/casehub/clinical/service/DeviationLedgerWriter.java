package io.casehub.clinical.service;

import io.casehub.clinical.api.model.PiApprovalStatus;
import io.casehub.clinical.entity.ProtocolDeviation;
import io.casehub.clinical.ledger.ProtocolDeviationLedgerEntry;
import io.casehub.ledger.api.model.ActorType;
import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.runtime.repository.LedgerEntryRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.UUID;

@ApplicationScoped
public class DeviationLedgerWriter {

    static final String SYSTEM_ACTOR = "clinical-service";

    @Inject
    LedgerEntryRepository ledgerEntryRepository;

    public void writeCommandEntry(ProtocolDeviation dev, String piId) {
        ProtocolDeviationLedgerEntry entry = baseEntry(dev);
        entry.entryType = LedgerEntryType.COMMAND;
        entry.actorId = SYSTEM_ACTOR;
        entry.actorType = ActorType.SYSTEM;
        entry.actorRole = "deviation-reporter";
        entry.occurredAt = dev.commandedAt;
        entry.piId = piId;
        entry.commandedAt = dev.commandedAt;
        entry.responseDeadline = dev.responseDeadline;
        entry.escalationRequirement = dev.escalationRequirement != null
            ? dev.escalationRequirement.name() : null;
        ledgerEntryRepository.save(entry);
    }

    public void writeResolutionEntry(ProtocolDeviation dev, PiApprovalStatus terminalStatus,
                                     String actorId, ActorType actorType, String actorRole) {
        ProtocolDeviationLedgerEntry entry = baseEntry(dev);
        entry.entryType = LedgerEntryType.EVENT;
        entry.actorId = actorId;
        entry.actorType = actorType;
        entry.actorRole = actorRole;
        entry.occurredAt = Instant.now();
        entry.terminalStatus = terminalStatus.name();
        entry.resolvedAt = entry.occurredAt;
        ledgerEntryRepository.save(entry);
    }

    private ProtocolDeviationLedgerEntry baseEntry(ProtocolDeviation dev) {
        ProtocolDeviationLedgerEntry entry = new ProtocolDeviationLedgerEntry();
        entry.id = UUID.randomUUID();
        entry.subjectId = dev.id;
        entry.sequenceNumber = nextSequenceNumber(dev.id);
        entry.deviationId = dev.id;
        entry.siteId = dev.siteId;
        entry.severity = dev.severity.name();
        return entry;
    }

    private int nextSequenceNumber(UUID deviationId) {
        return ledgerEntryRepository.findLatestBySubjectId(deviationId)
            .map(e -> e.sequenceNumber + 1)
            .orElse(1);
    }
}
