package io.casehub.clinical.service;

import io.casehub.clinical.api.model.PiApprovalStatus;
import io.casehub.clinical.entity.ProtocolDeviation;
import io.casehub.clinical.ledger.ProtocolDeviationLedgerEntry;
import io.casehub.platform.api.identity.ActorType;
import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.runtime.repository.LedgerEntryRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Centralises all tamper-evident ledger writes for the protocol deviation PI authorisation lifecycle.
 *
 * Owns sequenceNumber computation via findLatestBySubjectId — ensuring each entry in a
 * deviation's audit chain has a unique, incrementing position regardless of which service writes it.
 *
 * GCP / ICH E6(R3) compliance: every deviation must have a ledgered COMMAND (obligation issued)
 * and a ledgered resolution (APPROVED, REJECTED, ESCALATED, or EXPIRED). Without both ends of
 * the chain, an FDA inspector can see a PI was commanded but not how the obligation was discharged.
 *
 * Note: SYSTEM_ACTOR and ProtocolDeviationService.CLINICAL_SENDER are the same string
 * "clinical-service" — both identify the clinical harness in their respective contexts
 * (ledger actor vs qhorus message sender). Keep them in sync if the harness identity changes.
 */
@ApplicationScoped
public class DeviationLedgerWriter {

    static final String SYSTEM_ACTOR = "clinical-service";

    @Inject
    LedgerEntryRepository ledgerEntryRepository;

    @Inject
    Clock clock;

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
        entry.occurredAt = clock.instant();
        entry.terminalStatus = terminalStatus.name();
        entry.resolvedAt = entry.occurredAt;
        ledgerEntryRepository.save(entry);
    }

    /**
     * Records a sponsor notification event in the ledger.
     *
     * {@code notifiedAt} is caller-supplied (the connector delivery timestamp) rather than clock.instant()
     * so that the ledger accurately records when delivery was attempted, not when the record was written.
     */
    public void writeSponsorNotifiedEntry(ProtocolDeviation dev, Instant notifiedAt, boolean delivered) {
        ProtocolDeviationLedgerEntry entry = baseEntry(dev);
        entry.entryType = LedgerEntryType.EVENT;
        entry.actorId = SYSTEM_ACTOR;
        entry.actorType = ActorType.SYSTEM;
        entry.actorRole = delivered ? "sponsor-notifier" : "sponsor-notifier-failed";
        entry.occurredAt = notifiedAt;
        entry.sponsorNotifiedAt = delivered ? notifiedAt : null;
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
