package io.casehub.clinical.service;

import io.casehub.clinical.api.ClinicalActors;
import io.casehub.clinical.api.model.DeviationSeverity;
import io.casehub.clinical.api.model.PiApprovalStatus;
import io.casehub.clinical.entity.ProtocolDeviation;
import io.casehub.clinical.ledger.ProtocolDeviationLedgerEntry;
import io.casehub.platform.api.identity.ActorType;
import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.runtime.repository.LedgerEntryRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

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
 * The clinical harness uses {@link ClinicalActors#CLINICAL_SERVICE} as its system actorId
 * across all ledger writers; {@code ProtocolDeviationService.CLINICAL_SENDER} uses the same
 * identity string in the qhorus context — keep both in sync if the harness identity changes.
 */
@ApplicationScoped
public class DeviationLedgerWriter {

    @Inject
    LedgerEntryRepository ledgerEntryRepository;

    @Inject
    Clock clock;

    public void writeCommandEntry(ProtocolDeviation dev, String piId) {
        ProtocolDeviationLedgerEntry entry = baseEntry(dev);
        entry.entryType = LedgerEntryType.COMMAND;
        entry.actorId = ClinicalActors.CLINICAL_SERVICE;
        entry.actorType = ActorType.SYSTEM;
        entry.actorRole = "deviation-reporter";
        entry.occurredAt = dev.commandedAt;
        entry.piId = piId;
        entry.commandedAt = dev.commandedAt;
        entry.responseDeadline = dev.responseDeadline;
        entry.escalationRequirement = dev.escalationRequirement != null
            ? dev.escalationRequirement.name() : null;
        ledgerEntryRepository.save(entry, "default");
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
        ledgerEntryRepository.save(entry, "default");
    }

    /**
     * Records successful sponsor notification delivery in the deviation's audit chain.
     *
     * <p>Takes fields from a {@code SponsorNotification} snapshot so the delivery service does not
     * need to load {@code ProtocolDeviation} in Phase 3. Only caller was {@code DefaultSponsorNotifier}
     * (deleted) — this overload replaces it for the durable notifier path.
     *
     * <p>{@code notifiedAt} is caller-supplied (connector acknowledgement time) so both audit chains
     * record the same timestamp for the same delivery event.
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void writeSponsorNotifiedEntry(UUID deviationId, UUID siteId, DeviationSeverity severity,
                                          Instant notifiedAt, String piId, String piDisplayName) {
        final var entry = new ProtocolDeviationLedgerEntry();
        entry.id = UUID.randomUUID();
        entry.subjectId = deviationId;
        entry.sequenceNumber = nextSequenceNumber(deviationId);
        entry.deviationId = deviationId;
        entry.siteId = siteId;
        entry.severity = severity.name();
        entry.entryType = LedgerEntryType.EVENT;
        entry.actorId = ClinicalActors.CLINICAL_SERVICE;
        entry.actorType = ActorType.SYSTEM;
        entry.actorRole = "sponsor-notifier";
        entry.occurredAt = notifiedAt;
        entry.sponsorNotifiedAt = notifiedAt;
        entry.piId = piId;
        entry.piDisplayName = piDisplayName;
        ledgerEntryRepository.save(entry, "default");
    }

    /**
     * Records terminal notification exhaustion in the deviation's audit chain.
     *
     * <p>Distinct from {@link #writeObserverFailureEntry} — "exhausted" means we attempted N
     * times and the sponsor remains unreached; "observer-failed" means listener-level CDI failure.
     * An FDA auditor must be able to distinguish these two failure modes.
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void writeExhaustedNotificationEntry(UUID deviationId, UUID siteId,
                                                 DeviationSeverity severity, Instant occurredAt) {
        final var entry = new ProtocolDeviationLedgerEntry();
        entry.id = UUID.randomUUID();
        entry.subjectId = deviationId;
        entry.sequenceNumber = nextSequenceNumber(deviationId);
        entry.deviationId = deviationId;
        entry.siteId = siteId;
        entry.severity = severity.name();
        entry.entryType = LedgerEntryType.EVENT;
        entry.actorId = ClinicalActors.CLINICAL_SERVICE;
        entry.actorType = ActorType.SYSTEM;
        entry.actorRole = "sponsor-notifier-exhausted";
        entry.occurredAt = occurredAt;
        entry.sponsorNotifiedAt = null;
        ledgerEntryRepository.save(entry, "default");
    }

    /**
     * Called from SponsorNotificationListener deliberate early-return paths (missing site, trial, or config).
     * Distinct actorRole per reason satisfies ICH E6(R3) §5.17 — the audit trail must explain why notification
     * was not sent, not merely record that it wasn't.
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void writeSkippedSponsorEntry(UUID deviationId, UUID siteId,
            DeviationSeverity severity, Instant now, String reason) {
        var entry = new ProtocolDeviationLedgerEntry();
        entry.id = UUID.randomUUID();
        entry.subjectId = deviationId;
        entry.sequenceNumber = nextSequenceNumber(deviationId);
        entry.deviationId = deviationId;
        entry.siteId = siteId;
        entry.severity = severity.name();
        entry.entryType = LedgerEntryType.EVENT;
        entry.actorId = ClinicalActors.CLINICAL_SERVICE;
        entry.actorType = ActorType.SYSTEM;
        entry.actorRole = reason;
        entry.occurredAt = now;
        entry.sponsorNotifiedAt = null;
        ledgerEntryRepository.save(entry, "default");
    }

    /** Called from SponsorNotificationListener observer fallback path. Commits in its own REQUIRES_NEW transaction. */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void writeObserverFailureEntry(UUID deviationId, UUID siteId,
            DeviationSeverity severity, Instant now) {
        var entry = new ProtocolDeviationLedgerEntry();
        entry.id = UUID.randomUUID();
        entry.subjectId = deviationId;
        entry.sequenceNumber = nextSequenceNumber(deviationId);
        entry.deviationId = deviationId;
        entry.siteId = siteId;
        entry.severity = severity.name();
        entry.entryType = LedgerEntryType.EVENT;
        entry.actorId = ClinicalActors.CLINICAL_SERVICE;
        entry.actorType = ActorType.SYSTEM;
        entry.actorRole = "sponsor-notifier-observer-failed";
        entry.occurredAt = now;
        entry.sponsorNotifiedAt = null;
        ledgerEntryRepository.save(entry, "default");
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
        return ledgerEntryRepository.findLatestBySubjectId(deviationId, "default")
            .map(e -> e.sequenceNumber + 1)
            .orElse(1);
    }
}
