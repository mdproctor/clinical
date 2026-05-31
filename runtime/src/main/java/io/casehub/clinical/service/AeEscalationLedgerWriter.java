package io.casehub.clinical.service;

import io.casehub.clinical.api.ClinicalActors;
import io.casehub.clinical.api.model.CtcaeGrade;
import io.casehub.clinical.ledger.AeEscalationLedgerEntry;
import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.runtime.repository.LedgerEntryRepository;
import io.casehub.platform.api.identity.ActorType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
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
        entry.actorId = ClinicalActors.CLINICAL_SERVICE;
        entry.actorType = ActorType.SYSTEM;
        entry.actorRole = "AeEscalationCase";
        entry.occurredAt = clock.instant();
        entry.aeId = aeId;
        entry.enrollmentId = enrollmentId;
        entry.ctcaeGrade = grade != null ? grade.name() : null;
        entry.safetyReviewOutcome = safetyReviewOutcome;
        entry.dsmbEscalated = dsmbEscalated;
        entry.completedAt = completedAt;
        ledgerEntryRepository.save(entry);
    }

    /**
     * Called from AeEscalationListener observer fallback path.
     * Commits in its own REQUIRES_NEW transaction so it persists even if the
     * outer transaction is in rollback-only state.
     * grade may be null if the case context contained no valid grade value
     * (grade is resolved outside the try block, before this method is called).
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void writeObserverFailureEntry(UUID aeId, UUID enrollmentId, CtcaeGrade grade) {
        Instant now = clock.instant();
        var entry = new AeEscalationLedgerEntry();
        entry.id = UUID.randomUUID();
        entry.subjectId = aeId;
        entry.sequenceNumber = nextSequenceNumber(aeId);
        entry.entryType = LedgerEntryType.EVENT;
        entry.actorId = ClinicalActors.CLINICAL_SERVICE;
        entry.actorType = ActorType.SYSTEM;
        entry.actorRole = "AeEscalationCase-observer-failed";
        entry.occurredAt = now;
        entry.aeId = aeId;
        entry.enrollmentId = enrollmentId;
        entry.ctcaeGrade = grade != null ? grade.name() : null;
        entry.dsmbEscalated = false;
        entry.completedAt = now;
        ledgerEntryRepository.save(entry);
    }

    private int nextSequenceNumber(UUID aeId) {
        return ledgerEntryRepository.findLatestBySubjectId(aeId)
                .map(e -> e.sequenceNumber + 1)
                .orElse(1);
    }
}
