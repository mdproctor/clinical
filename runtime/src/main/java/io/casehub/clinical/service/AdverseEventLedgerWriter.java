package io.casehub.clinical.service;

import io.casehub.clinical.entity.AdverseEvent;
import io.casehub.clinical.ledger.AdverseEventLedgerEntry;
import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.runtime.repository.LedgerEntryRepository;
import io.casehub.platform.api.identity.ActorType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Clock;
import java.util.UUID;

/**
 * Centralises all tamper-evident ledger writes for the adverse event safety lifecycle.
 *
 * Owns sequenceNumber computation via findLatestBySubjectId, ensuring each entry in an
 * adverse event's audit chain has a unique, incrementing position regardless of which
 * service writes it. Mirrors DeviationLedgerWriter — extend here when Epic 4 adds
 * resolution and escalation entries.
 *
 * FDA IND / GCP requirement: every safety event must have an independently verifiable
 * audit trail from initial report through final disposition.
 */
@ApplicationScoped
public class AdverseEventLedgerWriter {

    @Inject LedgerEntryRepository ledgerEntryRepository;
    @Inject Clock clock;

    public void writeReportEntry(AdverseEvent ae) {
        AdverseEventLedgerEntry entry = new AdverseEventLedgerEntry();
        entry.id = UUID.randomUUID();
        entry.subjectId = ae.id;
        entry.sequenceNumber = nextSequenceNumber(ae.id);
        entry.entryType = LedgerEntryType.EVENT;
        entry.actorId = "system";
        entry.actorType = ActorType.SYSTEM;
        entry.actorRole = "AdverseEventReporter";
        entry.occurredAt = clock.instant();
        entry.adverseEventId = ae.id;
        entry.enrollmentId = ae.enrollmentId;
        entry.ctcaeGrade = ae.grade.name();
        entry.reportedAt = ae.reportedAt;
        entry.slaDeadline = ae.slaDeadline;
        ledgerEntryRepository.save(entry);
    }

    private int nextSequenceNumber(UUID aeId) {
        return ledgerEntryRepository.findLatestBySubjectId(aeId)
            .map(e -> e.sequenceNumber + 1)
            .orElse(1);
    }
}
