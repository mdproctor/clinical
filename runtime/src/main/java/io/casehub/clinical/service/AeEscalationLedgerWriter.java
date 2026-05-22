package io.casehub.clinical.service;

import io.casehub.clinical.api.model.CtcaeGrade;
import io.casehub.clinical.ledger.AeEscalationLedgerEntry;
import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.runtime.repository.LedgerEntryRepository;
import io.casehub.platform.api.identity.ActorType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/** Writes tamper-evident ledger entries for AE escalation case completions. */
@ApplicationScoped
public class AeEscalationLedgerWriter {

    @Inject LedgerEntryRepository ledgerEntryRepository;
    @Inject Clock clock;

    public void writeCompletionEntry(
            UUID aeId,
            UUID enrollmentId,
            CtcaeGrade grade,
            String safetyReviewOutcome,
            boolean dsmbEscalated,
            Instant completedAt) {
        var entry = new AeEscalationLedgerEntry();
        entry.id = UUID.randomUUID();
        entry.subjectId = aeId;
        entry.sequenceNumber = nextSequenceNumber(aeId);
        entry.entryType = LedgerEntryType.EVENT;
        entry.actorId = "system";
        entry.actorType = ActorType.SYSTEM;
        entry.actorRole = "AeEscalationCase";
        entry.occurredAt = clock.instant();
        entry.aeId = aeId;
        entry.enrollmentId = enrollmentId;
        entry.ctcaeGrade = grade.name();
        entry.safetyReviewOutcome = safetyReviewOutcome;
        entry.dsmbEscalated = dsmbEscalated;
        entry.completedAt = completedAt;
        ledgerEntryRepository.save(entry);
    }

    private int nextSequenceNumber(UUID aeId) {
        return ledgerEntryRepository.findLatestBySubjectId(aeId)
                .map(e -> e.sequenceNumber + 1)
                .orElse(1);
    }
}
