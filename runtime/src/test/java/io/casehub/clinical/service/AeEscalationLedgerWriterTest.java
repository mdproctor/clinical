package io.casehub.clinical.service;

import io.casehub.clinical.api.model.CtcaeGrade;
import io.casehub.clinical.ledger.AeEscalationLedgerEntry;
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
class AeEscalationLedgerWriterTest {

    @Mock LedgerEntryRepository ledgerEntryRepository;
    @Mock Clock clock;
    @InjectMocks AeEscalationLedgerWriter writer;

    @Test
    void writeCompletionEntry_persists_correct_fields() {
        Instant now = Instant.parse("2026-05-22T14:00:00Z");
        UUID aeId = UUID.randomUUID();
        UUID enrollmentId = UUID.randomUUID();
        when(clock.instant()).thenReturn(now);
        when(ledgerEntryRepository.findLatestBySubjectId(any(), any())).thenReturn(Optional.empty());

        writer.writeCompletionEntry(aeId, enrollmentId, CtcaeGrade.GRADE_4, "REVIEWED", true, now);

        ArgumentCaptor<AeEscalationLedgerEntry> captor =
                ArgumentCaptor.forClass(AeEscalationLedgerEntry.class);
        verify(ledgerEntryRepository).save(captor.capture(), any());

        AeEscalationLedgerEntry entry = captor.getValue();
        assertThat(entry.aeId).isEqualTo(aeId);
        assertThat(entry.enrollmentId).isEqualTo(enrollmentId);
        assertThat(entry.ctcaeGrade).isEqualTo("GRADE_4");
        assertThat(entry.safetyReviewOutcome).isEqualTo("REVIEWED");
        assertThat(entry.dsmbEscalated).isTrue();
        assertThat(entry.completedAt).isEqualTo(now);
        assertThat(entry.sequenceNumber).isEqualTo(1);
        assertThat(entry.actorId).isEqualTo("clinical-service");
        assertThat(entry.actorType).isEqualTo(ActorType.SYSTEM);
    }

    @Test
    void writeCompletionEntry_with_null_grade_stores_null() {
        Instant now = Instant.parse("2026-05-31T10:00:00Z");
        UUID aeId = UUID.randomUUID();
        UUID enrollmentId = UUID.randomUUID();
        when(clock.instant()).thenReturn(now);
        when(ledgerEntryRepository.findLatestBySubjectId(any(), any())).thenReturn(Optional.empty());

        writer.writeCompletionEntry(aeId, enrollmentId, null, null, false, now);

        ArgumentCaptor<AeEscalationLedgerEntry> captor =
                ArgumentCaptor.forClass(AeEscalationLedgerEntry.class);
        verify(ledgerEntryRepository).save(captor.capture(), any());
        assertThat(captor.getValue().ctcaeGrade).isNull();
    }

    @Test
    void writeObserverFailureEntry_with_null_grade_saves_null_grade() {
        Instant now = Instant.parse("2026-05-31T10:00:00Z");
        UUID aeId = UUID.randomUUID();
        UUID enrollmentId = UUID.randomUUID();
        when(clock.instant()).thenReturn(now);
        when(ledgerEntryRepository.findLatestBySubjectId(any(), any())).thenReturn(Optional.empty());

        writer.writeObserverFailureEntry(aeId, enrollmentId, null);

        ArgumentCaptor<AeEscalationLedgerEntry> captor =
                ArgumentCaptor.forClass(AeEscalationLedgerEntry.class);
        verify(ledgerEntryRepository).save(captor.capture(), any());
        AeEscalationLedgerEntry entry = captor.getValue();
        assertThat(entry.ctcaeGrade).isNull();
        assertThat(entry.actorRole).isEqualTo("AeEscalationCase-observer-failed");
        assertThat(entry.aeId).isEqualTo(aeId);
        assertThat(entry.enrollmentId).isEqualTo(enrollmentId);
        assertThat(entry.dsmbEscalated).isFalse();
        assertThat(entry.actorId).isEqualTo("clinical-service");
        assertThat(entry.actorType).isEqualTo(ActorType.SYSTEM);
    }

    @Test
    void writeObserverFailureEntry_with_valid_grade_saves_grade() {
        Instant now = Instant.parse("2026-05-31T10:00:00Z");
        UUID aeId = UUID.randomUUID();
        UUID enrollmentId = UUID.randomUUID();
        when(clock.instant()).thenReturn(now);
        when(ledgerEntryRepository.findLatestBySubjectId(any(), any())).thenReturn(Optional.empty());

        writer.writeObserverFailureEntry(aeId, enrollmentId, CtcaeGrade.GRADE_3);

        ArgumentCaptor<AeEscalationLedgerEntry> captor =
                ArgumentCaptor.forClass(AeEscalationLedgerEntry.class);
        verify(ledgerEntryRepository).save(captor.capture(), any());
        assertThat(captor.getValue().ctcaeGrade).isEqualTo("GRADE_3");
    }
}
