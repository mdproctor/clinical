package io.casehub.clinical.service;

import io.casehub.clinical.api.ClinicalActors;
import io.casehub.clinical.api.model.CtcaeGrade;
import io.casehub.clinical.ledger.SafetyOfficerNotificationLedgerEntry;
import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.runtime.repository.LedgerEntryRepository;
import io.casehub.platform.api.identity.ActorType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Writes tamper-evident ledger entries for safety officer AE notification attempts.
 * Records both successful and failed delivery to satisfy ICH E6(R3) §5.17 / 21 CFR 312.32.
 */
@ApplicationScoped
public class SafetyOfficerNotificationLedgerWriter {

    @Inject LedgerEntryRepository ledgerEntryRepository;
    @Inject Clock clock;

    public void writeEntry(
            UUID aeId,
            UUID enrollmentId,
            UUID siteId,
            CtcaeGrade grade,
            String connectorId,
            String destination,
            boolean delivered) {
        final Instant now = clock.instant();
        var entry = new SafetyOfficerNotificationLedgerEntry();
        entry.id = UUID.randomUUID();
        entry.subjectId = aeId;
        entry.sequenceNumber = nextSequenceNumber(aeId);
        entry.entryType = LedgerEntryType.EVENT;
        entry.actorId = ClinicalActors.CLINICAL_SERVICE;
        entry.actorType = ActorType.SYSTEM;
        entry.actorRole = "SafetyOfficerNotification";
        entry.occurredAt = now;
        entry.aeId = aeId;
        entry.enrollmentId = enrollmentId;
        entry.siteId = siteId;
        entry.ctcaeGrade = grade.name();
        entry.connectorId = connectorId;
        entry.destination = destination;
        entry.delivered = delivered;
        entry.notifiedAt = now;
        ledgerEntryRepository.save(entry, "default");
    }

    /**
     * Called from SafetyOfficerNotificationListener observer fallback path. Commits in its own REQUIRES_NEW transaction.
     * connectorId and destination are null — the error occurred before connector config was reachable, so no
     * notification was attempted. notifiedAt records when this fallback entry was written (column is NOT NULL).
     * Use connectorId=null to distinguish observer-level failures from connector delivery failures.
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void writeObserverFailureEntry(UUID aeId, UUID enrollmentId, UUID siteId, CtcaeGrade grade) {
        writeEntry(aeId, enrollmentId, siteId, grade, null, null, false);
    }

    /**
     * Called from SafetyOfficerNotificationListener deliberate early-return paths (missing site, trial, or config).
     * Distinct actorRole per reason satisfies ICH E6(R3) §5.17 — the audit trail must explain why notification
     * was not sent, not merely record that it wasn't.
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void writeSkippedEntry(UUID aeId, UUID enrollmentId, UUID siteId, CtcaeGrade grade, String reason) {
        final Instant now = clock.instant();
        var entry = new SafetyOfficerNotificationLedgerEntry();
        entry.id = UUID.randomUUID();
        entry.subjectId = aeId;
        entry.sequenceNumber = nextSequenceNumber(aeId);
        entry.entryType = LedgerEntryType.EVENT;
        entry.actorId = ClinicalActors.CLINICAL_SERVICE;
        entry.actorType = ActorType.SYSTEM;
        entry.actorRole = reason;
        entry.occurredAt = now;
        entry.aeId = aeId;
        entry.enrollmentId = enrollmentId;
        entry.siteId = siteId;
        entry.ctcaeGrade = grade.name();
        entry.connectorId = null;
        entry.destination = null;
        entry.delivered = false;
        entry.notifiedAt = now;
        ledgerEntryRepository.save(entry, "default");
    }

    private int nextSequenceNumber(final UUID aeId) {
        return ledgerEntryRepository.findLatestBySubjectId(aeId, "default")
            .map(e -> e.sequenceNumber + 1)
            .orElse(1);
    }
}
