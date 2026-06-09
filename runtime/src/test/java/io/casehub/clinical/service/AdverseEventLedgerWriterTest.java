package io.casehub.clinical.service;

import io.casehub.clinical.entity.AdverseEvent;
import io.casehub.clinical.ledger.AdverseEventLedgerEntry;
import io.casehub.clinical.api.model.CtcaeGrade;
import io.casehub.platform.api.identity.ActorType;
import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.runtime.model.LedgerEntry;
import io.casehub.ledger.runtime.repository.LedgerEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdverseEventLedgerWriterTest {

    @Mock LedgerEntryRepository ledgerEntryRepository;
    @Mock Clock clock;
    @InjectMocks AdverseEventLedgerWriter writer;

    private static final Instant FIXED_INSTANT = Instant.parse("2026-05-22T10:00:00Z");
    private AdverseEvent ae;

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenReturn(FIXED_INSTANT);
        when(ledgerEntryRepository.save(any(), any())).thenAnswer(i -> i.getArgument(0));

        ae = new AdverseEvent();
        ae.id = UUID.randomUUID();
        ae.enrollmentId = UUID.randomUUID();
        ae.grade = CtcaeGrade.GRADE_3;
        ae.reportedAt = FIXED_INSTANT;
        ae.slaDeadline = FIXED_INSTANT.plusSeconds(86400);
    }

    @Test
    void writeReportEntry_sequenceNumber1WhenNoPriorEntries() {
        when(ledgerEntryRepository.findLatestBySubjectId(eq(ae.id), any())).thenReturn(Optional.empty());

        writer.writeReportEntry(ae);

        assertThat(captureEntry().sequenceNumber).isEqualTo(1);
    }

    @Test
    void writeReportEntry_sequenceNumberIncrementsFromPrior() {
        LedgerEntry prior = new AdverseEventLedgerEntry();
        prior.sequenceNumber = 2;
        when(ledgerEntryRepository.findLatestBySubjectId(eq(ae.id), any())).thenReturn(Optional.of(prior));

        writer.writeReportEntry(ae);

        assertThat(captureEntry().sequenceNumber).isEqualTo(3);
    }

    @Test
    void writeReportEntry_setsCorrectFields() {
        when(ledgerEntryRepository.findLatestBySubjectId(eq(ae.id), any())).thenReturn(Optional.empty());

        writer.writeReportEntry(ae);

        AdverseEventLedgerEntry entry = captureEntry();
        assertThat(entry.entryType).isEqualTo(LedgerEntryType.EVENT);
        assertThat(entry.actorId).isEqualTo("clinical-service");
        assertThat(entry.actorType).isEqualTo(ActorType.SYSTEM);
        assertThat(entry.actorRole).isEqualTo("AdverseEventReporter");
        assertThat(entry.occurredAt).isEqualTo(FIXED_INSTANT);
        assertThat(entry.subjectId).isEqualTo(ae.id);
        assertThat(entry.adverseEventId).isEqualTo(ae.id);
        assertThat(entry.enrollmentId).isEqualTo(ae.enrollmentId);
        assertThat(entry.ctcaeGrade).isEqualTo("GRADE_3");
        assertThat(entry.reportedAt).isEqualTo(FIXED_INSTANT);
        assertThat(entry.slaDeadline).isEqualTo(ae.slaDeadline);
        assertThat(entry.id).isNotNull();
    }

    private AdverseEventLedgerEntry captureEntry() {
        ArgumentCaptor<LedgerEntry> captor = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledgerEntryRepository).save(captor.capture(), any());
        return (AdverseEventLedgerEntry) captor.getValue();
    }
}
