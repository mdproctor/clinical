package io.casehub.clinical.service;

import io.casehub.clinical.api.ClinicalActors;
import io.casehub.clinical.api.model.DeviationSeverity;
import io.casehub.clinical.api.model.PiApprovalStatus;
import io.casehub.clinical.api.model.SponsorNotificationStatus;
import io.casehub.clinical.entity.SponsorNotification;
import io.casehub.clinical.ledger.SponsorNotificationLedgerEntry;
import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.runtime.model.LedgerEntry;
import io.casehub.ledger.runtime.repository.LedgerEntryRepository;
import io.casehub.platform.api.identity.ActorType;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SponsorNotificationLedgerWriterTest {

    @Mock LedgerEntryRepository repo;
    @Mock Clock clock;
    @InjectMocks SponsorNotificationLedgerWriter writer;

    private static final Instant FIXED = Instant.parse("2026-06-05T10:00:00Z");
    private SponsorNotification notification;

    @BeforeEach
    void setUp() {
        lenient().when(clock.instant()).thenReturn(FIXED);
        when(repo.save(any(), any())).thenAnswer(i -> i.getArgument(0));
        when(repo.findLatestBySubjectId(any(), any())).thenReturn(Optional.empty());

        notification = new SponsorNotification();
        notification.id = UUID.randomUUID();
        notification.deviationId = UUID.randomUUID();
        notification.trialId = UUID.randomUUID();
        notification.siteId = UUID.randomUUID();
        notification.status = SponsorNotificationStatus.PENDING;
        notification.severity = DeviationSeverity.MAJOR;
        notification.terminalStatus = PiApprovalStatus.ESCALATED;
        notification.deviationType = "CONSENT_DEVIATION";
        notification.connectorId = "slack";
        notification.destination = "https://hooks.slack.com/test";
        notification.piId = "dr-smith@v1";
        notification.piDisplayName = "Dr. Smith";
    }

    // ── writeDelivered ────────────────────────────────────────────────────────

    @Test
    void writeDelivered_uses_caller_supplied_deliveredAt_not_clock() {
        final Instant deliveredAt = Instant.parse("2026-06-05T09:55:00Z");

        writer.writeDelivered(notification, 1, deliveredAt);

        final SponsorNotificationLedgerEntry entry = captureEntry();
        assertThat(entry.occurredAt).isEqualTo(deliveredAt);
        // clock.instant() returns FIXED (10:00); deliveredAt is 09:55 — they differ
        assertThat(entry.occurredAt).isNotEqualTo(FIXED);
    }

    @Test
    void writeDelivered_sets_correct_actor_and_role() {
        writer.writeDelivered(notification, 1, FIXED);

        final SponsorNotificationLedgerEntry entry = captureEntry();
        assertThat(entry.actorRole).isEqualTo("sponsor-notifier");
        assertThat(entry.actorId).isEqualTo(ClinicalActors.CLINICAL_SERVICE);
        assertThat(entry.actorType).isEqualTo(ActorType.SYSTEM);
        assertThat(entry.entryType).isEqualTo(LedgerEntryType.EVENT);
        assertThat(entry.delivered).isTrue();
        assertThat(entry.failureReason).isNull();
    }

    @Test
    void writeDelivered_subject_is_notificationId() {
        writer.writeDelivered(notification, 1, FIXED);

        final SponsorNotificationLedgerEntry entry = captureEntry();
        assertThat(entry.subjectId).isEqualTo(notification.id);
        assertThat(entry.notificationId).isEqualTo(notification.id);
        assertThat(entry.deviationId).isEqualTo(notification.deviationId);
    }

    @Test
    void writeDelivered_sets_sequence_number_1_when_no_prior_entries() {
        writer.writeDelivered(notification, 1, FIXED);

        assertThat(captureEntry().sequenceNumber).isEqualTo(1);
    }

    @Test
    void writeDelivered_increments_sequence_number_from_prior_entry() {
        final LedgerEntry prior = new SponsorNotificationLedgerEntry();
        prior.sequenceNumber = 3;
        when(repo.findLatestBySubjectId(eq(notification.id), any())).thenReturn(Optional.of(prior));

        writer.writeDelivered(notification, 2, FIXED);

        assertThat(captureEntry().sequenceNumber).isEqualTo(4);
    }

    @Test
    void writeDelivered_sets_attempt_number() {
        writer.writeDelivered(notification, 2, FIXED);

        assertThat(captureEntry().attemptNumber).isEqualTo(2);
    }

    // ── writeFailed ───────────────────────────────────────────────────────────

    @Test
    void writeFailed_sets_attempt_failed_role_and_not_delivered() {
        writer.writeFailed(notification, 1, "Connector timeout");

        final SponsorNotificationLedgerEntry entry = captureEntry();
        assertThat(entry.actorRole).isEqualTo("sponsor-notifier-attempt-failed");
        assertThat(entry.delivered).isFalse();
        assertThat(entry.failureReason).isEqualTo("Connector timeout");
        assertThat(entry.occurredAt).isEqualTo(FIXED);
    }

    @Test
    void writeFailed_uses_clock_for_occurredAt() {
        writer.writeFailed(notification, 1, "fail");

        assertThat(captureEntry().occurredAt).isEqualTo(FIXED);
    }

    // ── writeExhausted ────────────────────────────────────────────────────────

    @Test
    void writeExhausted_sets_exhausted_role_and_not_delivered() {
        writer.writeExhausted(notification, 3, "All retries consumed");

        final SponsorNotificationLedgerEntry entry = captureEntry();
        assertThat(entry.actorRole).isEqualTo("sponsor-notifier-exhausted");
        assertThat(entry.delivered).isFalse();
        assertThat(entry.failureReason).isEqualTo("All retries consumed");
        assertThat(entry.actorId).isEqualTo(ClinicalActors.CLINICAL_SERVICE);
        assertThat(entry.attemptNumber).isEqualTo(3);
    }

    @Test
    void writeExhausted_subject_is_notificationId_not_deviationId() {
        writer.writeExhausted(notification, 3, "fail");

        final SponsorNotificationLedgerEntry entry = captureEntry();
        assertThat(entry.subjectId).isEqualTo(notification.id);
        assertThat(entry.notificationId).isEqualTo(notification.id);
        assertThat(entry.deviationId).isEqualTo(notification.deviationId);
        // Confirm not confused: subject ≠ deviationId
        assertThat(entry.subjectId).isNotEqualTo(notification.deviationId);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private SponsorNotificationLedgerEntry captureEntry() {
        final ArgumentCaptor<LedgerEntry> captor = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(repo).save(captor.capture(), any());
        return (SponsorNotificationLedgerEntry) captor.getValue();
    }
}
