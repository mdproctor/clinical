package io.casehub.clinical.service;

import io.casehub.clinical.api.ClinicalActors;
import io.casehub.clinical.entity.AdverseEvent;
import io.casehub.clinical.ledger.RegulatorySubmissionLedgerEntry;
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
 * Writes tamper-evident record when an unexpected Grade 3 (15-day, 21 CFR 312.32(c)(1)(ii))
 * or Grade 5 (7-day, 21 CFR 312.32(c)(1)(i)) AE triggers IND expedited safety reporting.
 *
 * <p>Written in Phase 1 of RegulatorySubmissionCaseService in the same transaction
 * as the status update. {@code @Transactional(MANDATORY)} ensures this is always
 * called within an existing transaction.
 *
 * <p>EU AI Act Art.12 compliance supplement attached via
 * {@link ClinicalComplianceSupplement#regulatorySubmission(io.casehub.clinical.api.model.CtcaeGrade)}.
 */
@ApplicationScoped
public class RegulatorySubmissionLedgerWriter {

    @Inject LedgerEntryRepository ledgerEntryRepository;
    @Inject Clock clock;

    @Transactional(Transactional.TxType.MANDATORY)
    public void writeEntry(AdverseEvent ae) {
        RegulatorySubmissionLedgerEntry entry = new RegulatorySubmissionLedgerEntry();
        entry.id = UUID.randomUUID();
        entry.subjectId = ae.enrollmentId;
        entry.sequenceNumber = nextSequenceNumber(ae.enrollmentId, "default");
        entry.entryType = LedgerEntryType.EVENT;
        entry.actorId = ClinicalActors.CLINICAL_SERVICE;
        entry.actorType = ActorType.SYSTEM;
        entry.actorRole = "RegulatorySubmission";
        Instant now = clock.instant();
        entry.occurredAt = now;
        entry.aeId = ae.id;
        entry.grade = ae.grade != null ? ae.grade.name() : null;
        entry.filedAt = now;
        entry.attach(ClinicalComplianceSupplement.regulatorySubmission(ae.grade));
        ledgerEntryRepository.save(entry, "default");
    }

    private int nextSequenceNumber(UUID enrollmentId, String tenantId) {
        return ledgerEntryRepository.findLatestBySubjectId(enrollmentId, tenantId)
                .map(e -> e.sequenceNumber + 1)
                .orElse(1);
    }
}
