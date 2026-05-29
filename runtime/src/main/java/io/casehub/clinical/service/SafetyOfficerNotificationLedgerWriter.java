package io.casehub.clinical.service;

import io.casehub.clinical.api.model.CtcaeGrade;
import io.casehub.clinical.ledger.SafetyOfficerNotificationLedgerEntry;
import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.runtime.repository.LedgerEntryRepository;
import io.casehub.platform.api.identity.ActorType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
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
        entry.actorId = "system";
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
        ledgerEntryRepository.save(entry);
    }

    private int nextSequenceNumber(final UUID aeId) {
        return ledgerEntryRepository.findLatestBySubjectId(aeId)
            .map(e -> e.sequenceNumber + 1)
            .orElse(1);
    }
}
