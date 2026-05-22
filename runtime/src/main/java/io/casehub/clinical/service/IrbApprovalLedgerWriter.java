package io.casehub.clinical.service;

import io.casehub.clinical.entity.IrbApproval;
import io.casehub.clinical.ledger.IrbApprovalLedgerEntry;
import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.runtime.repository.LedgerEntryRepository;
import io.casehub.platform.api.identity.ActorType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Clock;
import java.util.UUID;

/**
 * Writes tamper-evident ledger entries for IRB committee decisions.
 *
 * FDA IND / GCP requirement: IRB approval or rejection must be independently
 * verifiable in the audit chain. Mirrors AdverseEventLedgerWriter pattern.
 */
@ApplicationScoped
public class IrbApprovalLedgerWriter {

    @Inject LedgerEntryRepository ledgerEntryRepository;
    @Inject Clock clock;

    public void writeDecisionEntry(IrbApproval approval) {
        var entry = new IrbApprovalLedgerEntry();
        entry.id = UUID.randomUUID();
        entry.subjectId = approval.id;
        entry.sequenceNumber = nextSequenceNumber(approval.id);
        entry.entryType = LedgerEntryType.EVENT;
        entry.actorId = "irb-committee";
        entry.actorType = ActorType.HUMAN;
        entry.actorRole = "IrbCommittee";
        entry.occurredAt = clock.instant();
        entry.irbApprovalId = approval.id;
        entry.deviationId = approval.deviationId;
        entry.irbDecision = approval.decision.name();
        entry.committeeId = approval.committeeId;
        entry.decidedAt = clock.instant();
        ledgerEntryRepository.save(entry);
    }

    private int nextSequenceNumber(UUID approvalId) {
        return ledgerEntryRepository.findLatestBySubjectId(approvalId)
                .map(e -> e.sequenceNumber + 1)
                .orElse(1);
    }
}
