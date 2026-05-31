package io.casehub.clinical.service;

import io.casehub.clinical.api.ClinicalActors;
import io.casehub.clinical.api.model.IrbDecision;
import io.casehub.clinical.entity.IrbApproval;
import io.casehub.clinical.ledger.IrbApprovalLedgerEntry;
import io.casehub.ledger.runtime.repository.LedgerEntryRepository;
import io.casehub.platform.api.identity.ActorType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IrbApprovalLedgerWriterTest {

    @Mock LedgerEntryRepository ledgerEntryRepository;
    @Mock Clock clock;
    @InjectMocks IrbApprovalLedgerWriter writer;

    @Test
    void writeDecisionEntry_persists_correct_fields() {
        Instant now = Instant.parse("2026-05-22T10:00:00Z");
        when(clock.instant()).thenReturn(now);
        when(ledgerEntryRepository.findLatestBySubjectId(any())).thenReturn(Optional.empty());

        IrbApproval approval = new IrbApproval();
        approval.id = UUID.randomUUID();
        approval.deviationId = UUID.randomUUID();
        approval.siteId = UUID.randomUUID();
        approval.committeeId = "irb-oncology";
        approval.decision = IrbDecision.APPROVED;

        writer.writeDecisionEntry(approval);

        ArgumentCaptor<IrbApprovalLedgerEntry> captor =
                ArgumentCaptor.forClass(IrbApprovalLedgerEntry.class);
        verify(ledgerEntryRepository).save(captor.capture());

        IrbApprovalLedgerEntry entry = captor.getValue();
        assertThat(entry.irbApprovalId).isEqualTo(approval.id);
        assertThat(entry.deviationId).isEqualTo(approval.deviationId);
        assertThat(entry.irbDecision).isEqualTo("APPROVED");
        assertThat(entry.committeeId).isEqualTo("irb-oncology");
        assertThat(entry.decidedAt).isEqualTo(now);
        assertThat(entry.subjectId).isEqualTo(approval.id);
        assertThat(entry.sequenceNumber).isEqualTo(1);
    }

    @Test
    void writeObserverFailureEntry_uses_system_actor_and_irb_committee_observer_failed_role() {
        Instant now = Instant.parse("2026-05-31T10:00:00Z");
        when(clock.instant()).thenReturn(now);
        when(ledgerEntryRepository.findLatestBySubjectId(any())).thenReturn(Optional.empty());

        IrbApproval approval = new IrbApproval();
        approval.id = UUID.randomUUID();
        approval.deviationId = UUID.randomUUID();
        approval.siteId = UUID.randomUUID();
        approval.committeeId = "irb-oncology";
        approval.decision = IrbDecision.APPROVED;

        writer.writeObserverFailureEntry(approval);

        ArgumentCaptor<IrbApprovalLedgerEntry> captor =
                ArgumentCaptor.forClass(IrbApprovalLedgerEntry.class);
        verify(ledgerEntryRepository).save(captor.capture());
        IrbApprovalLedgerEntry entry = captor.getValue();
        assertThat(entry.actorId).isEqualTo(ClinicalActors.CLINICAL_SERVICE);
        assertThat(entry.actorType).isEqualTo(ActorType.SYSTEM);
        assertThat(entry.actorRole).isEqualTo("IrbCommittee-observer-failed");
        assertThat(entry.irbApprovalId).isEqualTo(approval.id);
        assertThat(entry.deviationId).isEqualTo(approval.deviationId);
        assertThat(entry.irbDecision).isEqualTo("APPROVED");
        assertThat(entry.committeeId).isEqualTo("irb-oncology");
        assertThat(entry.decidedAt).isEqualTo(now);
        assertThat(entry.subjectId).isEqualTo(approval.id);
    }
}
