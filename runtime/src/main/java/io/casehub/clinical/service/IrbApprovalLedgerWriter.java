package io.casehub.clinical.service;

import io.casehub.clinical.api.ClinicalActors;
import io.casehub.clinical.entity.IrbApproval;
import io.casehub.clinical.ledger.IrbApprovalLedgerEntry;
import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.runtime.repository.LedgerEntryRepository;
import io.casehub.platform.api.identity.ActorType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

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
        var now = clock.instant();
        entry.occurredAt = now;
        entry.irbApprovalId = approval.id;
        entry.deviationId = approval.deviationId;
        entry.irbDecision = approval.decision.name();
        entry.committeeId = approval.committeeId;
        entry.decidedAt = now;
        ledgerEntryRepository.save(entry);
    }

    /**
     * Called from IrbDecisionListener observer fallback path.
     * Commits in its own REQUIRES_NEW transaction so it persists even if the
     * outer transaction is in rollback-only state.
     * Uses ClinicalActors.CLINICAL_SERVICE — this records a system-level failure,
     * not the IRB committee's decision (which writeDecisionEntry records).
     * approval.decision is always non-null at call time (set as first statement in try block).
     * committeeId defensive null-guard handles corrupt data only.
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void writeObserverFailureEntry(IrbApproval approval) {
        var entry = new IrbApprovalLedgerEntry();
        entry.id = UUID.randomUUID();
        entry.subjectId = approval.id;
        entry.sequenceNumber = nextSequenceNumber(approval.id);
        entry.entryType = LedgerEntryType.EVENT;
        entry.actorId = ClinicalActors.CLINICAL_SERVICE;
        entry.actorType = ActorType.SYSTEM;
        entry.actorRole = "IrbCommittee-observer-failed";
        var now = clock.instant();
        entry.occurredAt = now;
        entry.irbApprovalId = approval.id;
        entry.deviationId = approval.deviationId;
        entry.irbDecision = approval.decision != null ? approval.decision.name() : "UNKNOWN";
        entry.committeeId = approval.committeeId != null ? approval.committeeId : "unknown";
        entry.decidedAt = now;
        ledgerEntryRepository.save(entry);
    }

    private int nextSequenceNumber(UUID approvalId) {
        return ledgerEntryRepository.findLatestBySubjectId(approvalId)
                .map(e -> e.sequenceNumber + 1)
                .orElse(1);
    }
}
