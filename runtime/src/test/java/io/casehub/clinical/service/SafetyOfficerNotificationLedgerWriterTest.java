package io.casehub.clinical.service;

import io.casehub.clinical.api.model.CtcaeGrade;
import io.casehub.clinical.ledger.SafetyOfficerNotificationLedgerEntry;
import io.casehub.ledger.runtime.repository.LedgerEntryRepository;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SafetyOfficerNotificationLedgerWriterTest {

    @Mock LedgerEntryRepository ledgerEntryRepository;
    @Mock Clock clock;
    @InjectMocks SafetyOfficerNotificationLedgerWriter writer;

    private final UUID aeId = UUID.randomUUID();
    private final UUID enrollmentId = UUID.randomUUID();
    private final UUID siteId = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-05-29T10:00:00Z");

    @Test
    void writeEntry_persists_correct_fields_on_successful_delivery() {
        when(clock.instant()).thenReturn(now);
        when(ledgerEntryRepository.findLatestBySubjectId(any(), any())).thenReturn(Optional.empty());

        writer.writeEntry(aeId, enrollmentId, siteId, CtcaeGrade.GRADE_5,
            "slack", "https://hooks.slack.com/test", true);

        SafetyOfficerNotificationLedgerEntry entry = captureEntry();
        assertThat(entry.subjectId).isEqualTo(aeId);
        assertThat(entry.aeId).isEqualTo(aeId);
        assertThat(entry.enrollmentId).isEqualTo(enrollmentId);
        assertThat(entry.siteId).isEqualTo(siteId);
        assertThat(entry.ctcaeGrade).isEqualTo("GRADE_5");
        assertThat(entry.connectorId).isEqualTo("slack");
        assertThat(entry.destination).isEqualTo("https://hooks.slack.com/test");
        assertThat(entry.delivered).isTrue();
        assertThat(entry.notifiedAt).isEqualTo(now);
        assertThat(entry.sequenceNumber).isEqualTo(1);
        assertThat(entry.actorId).isEqualTo("clinical-service");
        assertThat(entry.actorRole).isEqualTo("SafetyOfficerNotification");
    }

    @Test
    void writeEntry_sets_delivered_false_on_failed_delivery() {
        when(clock.instant()).thenReturn(now);
        when(ledgerEntryRepository.findLatestBySubjectId(any(), any())).thenReturn(Optional.empty());

        writer.writeEntry(aeId, enrollmentId, siteId, CtcaeGrade.GRADE_3,
            "teams", "https://teams.webhook/test", false);

        assertThat(captureEntry().delivered).isFalse();
    }

    @Test
    void writeEntry_increments_sequence_number_from_latest() {
        when(clock.instant()).thenReturn(now);
        SafetyOfficerNotificationLedgerEntry existing = new SafetyOfficerNotificationLedgerEntry();
        existing.sequenceNumber = 5;
        when(ledgerEntryRepository.findLatestBySubjectId(eq(aeId), any())).thenReturn(Optional.of(existing));

        writer.writeEntry(aeId, enrollmentId, siteId, CtcaeGrade.GRADE_4,
            "slack", "https://hooks.slack.com/test", true);

        assertThat(captureEntry().sequenceNumber).isEqualTo(6);
    }

    @Test
    void writeObserverFailureEntry_writes_delivered_false_with_null_connector_fields() {
        when(clock.instant()).thenReturn(now);
        when(ledgerEntryRepository.findLatestBySubjectId(any(), any())).thenReturn(Optional.empty());

        writer.writeObserverFailureEntry(aeId, enrollmentId, siteId, CtcaeGrade.GRADE_3);

        SafetyOfficerNotificationLedgerEntry entry = captureEntry();
        assertThat(entry.delivered).isFalse();
        assertThat(entry.connectorId).isNull();
        assertThat(entry.destination).isNull();
        assertThat(entry.aeId).isEqualTo(aeId);
        assertThat(entry.siteId).isEqualTo(siteId);
        assertThat(entry.enrollmentId).isEqualTo(enrollmentId);
        assertThat(entry.ctcaeGrade).isEqualTo("GRADE_3");
        assertThat(entry.actorId).isEqualTo("clinical-service");
        assertThat(entry.notifiedAt).isNotNull();
        assertThat(entry.sequenceNumber).isEqualTo(1);
    }

    private SafetyOfficerNotificationLedgerEntry captureEntry() {
        ArgumentCaptor<SafetyOfficerNotificationLedgerEntry> captor =
            ArgumentCaptor.forClass(SafetyOfficerNotificationLedgerEntry.class);
        verify(ledgerEntryRepository).save(captor.capture(), any());
        return captor.getValue();
    }
}
