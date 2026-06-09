package io.casehub.clinical.service;

import io.casehub.clinical.api.SponsorNotificationExhaustedEvent;
import io.casehub.clinical.api.SponsorNotificationRetryPolicy;
import io.casehub.clinical.api.model.DeviationSeverity;
import io.casehub.clinical.api.model.PiApprovalStatus;
import io.casehub.clinical.api.model.SponsorNotificationStatus;
import io.casehub.clinical.entity.SponsorNotification;
import io.casehub.connectors.Connector;
import io.casehub.connectors.ConnectorMessage;
import io.casehub.platform.api.preferences.MapPreferences;
import java.util.HashMap;
import io.casehub.platform.api.preferences.PreferenceProvider;
import io.casehub.platform.api.preferences.Preferences;
import io.casehub.platform.api.preferences.SettingsScope;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@QuarkusTest
class SponsorNotificationDeliveryServiceTest {

    @Inject SponsorNotificationDeliveryService delivery;
    @Inject SponsorNotificationStore store;
    @Inject TestSlackConnector slackConnector;
    @InjectMock Clock clock;
    @InjectMock DeviationLedgerWriter deviationLedgerWriter;
    @InjectMock SponsorNotificationLedgerWriter notificationLedgerWriter;
    @InjectMock PreferenceProvider preferenceProvider;
    @InjectMock jakarta.enterprise.event.Event<io.casehub.clinical.api.SponsorNotificationExhaustedEvent> exhaustedEvents;

    private static final Instant FIXED = Instant.parse("2026-06-05T10:00:00Z");
    private static final SponsorNotificationRetryPolicy THREE_ATTEMPTS =
            new SponsorNotificationRetryPolicy(3, Duration.ofMinutes(30), 1.0, null);
    private static final SponsorNotificationRetryPolicy ONE_ATTEMPT =
            new SponsorNotificationRetryPolicy(1, Duration.ofMinutes(30), 1.0, null);
    private static final SponsorNotificationRetryPolicy EXPONENTIAL =
            new SponsorNotificationRetryPolicy(5, Duration.ofMinutes(15), 2.0, null);
    private static final SponsorNotificationRetryPolicy EXPONENTIAL_CAPPED =
            new SponsorNotificationRetryPolicy(5, Duration.ofMinutes(15), 2.0, Duration.ofMinutes(60));

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenReturn(FIXED);
        final Preferences prefs = new MapPreferences(Map.of());
        when(preferenceProvider.resolve(any(SettingsScope.class))).thenReturn(prefs);
        slackConnector.reset();
    }

    // ── success path ──────────────────────────────────────────────────────────

    @Test
    void attemptDelivery_success_marks_entity_DELIVERED() {
        final UUID id = createPending("slack");

        delivery.attemptDelivery(id);

        assertThat(load(id).status).isEqualTo(SponsorNotificationStatus.DELIVERED);
        assertThat(load(id).attempts).isEqualTo(1);
        assertThat(load(id).deliveredAt).isNotNull();
        assertThat(load(id).lastAttemptedAt).isNotNull();
    }

    @Test
    void attemptDelivery_success_sends_to_connector() {
        final UUID id = createPending("slack");

        delivery.attemptDelivery(id);

        assertThat(slackConnector.sent()).hasSize(1);
        assertThat(slackConnector.sent().get(0).destination())
                .isEqualTo("https://hooks.slack.com/test");
    }

    @Test
    void attemptDelivery_success_writes_deviation_ledger_with_connector_ack_time() {
        final UUID id = createPending("slack");

        delivery.attemptDelivery(id);

        verify(deviationLedgerWriter).writeSponsorNotifiedEntry(
                any(), any(), any(), eq(FIXED), any(), any());
    }

    @Test
    void attemptDelivery_success_writes_notification_ledger() {
        final UUID id = createPending("slack");

        delivery.attemptDelivery(id);

        verify(notificationLedgerWriter).writeDelivered(any(), eq(1), eq(FIXED));
    }

    // ── failure with retries remaining ────────────────────────────────────────

    @Test
    void attemptDelivery_connector_throw_marks_FAILED_when_retries_remain() {
        final UUID id = createPending("slack");
        slackConnector.setShouldThrow(true);
        stubPolicy(THREE_ATTEMPTS);

        delivery.attemptDelivery(id);

        assertThat(load(id).status).isEqualTo(SponsorNotificationStatus.FAILED);
        assertThat(load(id).attempts).isEqualTo(1);
        assertThat(load(id).nextRetryAfter).isNotNull();
        assertThat(load(id).failureReason).isNotNull();
    }

    @Test
    void attemptDelivery_failure_nextRetryAfter_is_now_plus_retryInterval() {
        final UUID id = createPending("slack");
        slackConnector.setShouldThrow(true);
        stubPolicy(THREE_ATTEMPTS);

        delivery.attemptDelivery(id);

        final Instant expected = FIXED.plus(Duration.ofMinutes(30));
        assertThat(load(id).nextRetryAfter).isEqualTo(expected);
    }

    @Test
    void attemptDelivery_failure_writes_failed_notification_ledger_entry() {
        final UUID id = createPending("slack");
        slackConnector.setShouldThrow(true);
        stubPolicy(THREE_ATTEMPTS);

        delivery.attemptDelivery(id);

        verify(notificationLedgerWriter).writeFailed(any(), eq(1), any());
        verify(notificationLedgerWriter, never()).writeExhausted(any(), anyInt(), any());
    }

    @Test
    void attemptDelivery_failure_does_not_fire_exhausted_event_when_retries_remain() {
        final UUID id = createPending("slack");
        slackConnector.setShouldThrow(true);
        stubPolicy(THREE_ATTEMPTS);

        delivery.attemptDelivery(id);

        // No deviation-level exhaustion entry, no event
        verify(deviationLedgerWriter, never()).writeExhaustedNotificationEntry(any(), any(), any(), any());
    }

    // ── exhaustion path (maxAttempts reached) ─────────────────────────────────

    @Test
    void attemptDelivery_maxAttempts1_failure_marks_EXHAUSTED_immediately() {
        final UUID id = createPending("slack");
        slackConnector.setShouldThrow(true);
        stubPolicy(ONE_ATTEMPT);

        delivery.attemptDelivery(id);

        assertThat(load(id).status).isEqualTo(SponsorNotificationStatus.EXHAUSTED);
        assertThat(load(id).attempts).isEqualTo(1);
        assertThat(load(id).nextRetryAfter).isNull();
    }

    @Test
    void attemptDelivery_exhaustion_writes_exhausted_notification_ledger() {
        final UUID id = createPending("slack");
        slackConnector.setShouldThrow(true);
        stubPolicy(ONE_ATTEMPT);

        delivery.attemptDelivery(id);

        verify(notificationLedgerWriter).writeExhausted(any(), eq(1), any());
        verify(notificationLedgerWriter, never()).writeFailed(any(), anyInt(), any());
    }

    @Test
    void attemptDelivery_exhaustion_writes_deviation_ledger_exhaustion_entry() {
        final UUID id = createPending("slack");
        slackConnector.setShouldThrow(true);
        stubPolicy(ONE_ATTEMPT);

        delivery.attemptDelivery(id);

        verify(deviationLedgerWriter).writeExhaustedNotificationEntry(any(), any(), any(), eq(FIXED));
    }

    @Test
    void attemptDelivery_exhaustion_fires_SponsorNotificationExhaustedEvent() {
        final UUID id = createPending("slack");
        slackConnector.setShouldThrow(true);
        stubPolicy(ONE_ATTEMPT);

        delivery.attemptDelivery(id);

        // Verify the event was fired with the correct notificationId
        final var captor = org.mockito.ArgumentCaptor.forClass(
                io.casehub.clinical.api.SponsorNotificationExhaustedEvent.class);
        verify(exhaustedEvents).fireAsync(captor.capture());
        assertThat(captor.getValue().notificationId()).isEqualTo(id);
        assertThat(captor.getValue().totalAttempts()).isEqualTo(1);
        assertThat(load(id).status).isEqualTo(SponsorNotificationStatus.EXHAUSTED);
    }

    // ── connector not found ───────────────────────────────────────────────────

    @Test
    void attemptDelivery_unknown_connector_treated_as_failure() {
        final UUID id = createPending("unknown-connector");
        stubPolicy(THREE_ATTEMPTS);

        delivery.attemptDelivery(id);

        assertThat(load(id).status).isEqualTo(SponsorNotificationStatus.FAILED);
        assertThat(load(id).failureReason).contains("connector-not-found");
    }

    @Test
    void attemptDelivery_unknown_connector_with_maxAttempts1_goes_straight_to_EXHAUSTED() {
        final UUID id = createPending("unknown-connector");
        stubPolicy(ONE_ATTEMPT);

        delivery.attemptDelivery(id);

        assertThat(load(id).status).isEqualTo(SponsorNotificationStatus.EXHAUSTED);
    }

    // ── idempotency ───────────────────────────────────────────────────────────

    @Test
    void attemptDelivery_skips_if_already_DELIVERED() {
        final UUID id = createPending("slack");
        delivery.attemptDelivery(id);  // delivers successfully
        slackConnector.reset();

        delivery.attemptDelivery(id);  // second call — already terminal

        assertThat(slackConnector.sent()).isEmpty();
    }

    @Test
    void attemptDelivery_skips_if_already_EXHAUSTED() {
        final UUID id = createPending("slack");
        slackConnector.setShouldThrow(true);
        stubPolicy(ONE_ATTEMPT);
        delivery.attemptDelivery(id);  // exhausts
        slackConnector.reset();
        slackConnector.setShouldThrow(false);

        delivery.attemptDelivery(id);  // second call — already terminal

        assertThat(slackConnector.sent()).isEmpty();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    UUID createPending(final String connectorId) {
        final UUID deviationId = UUID.randomUUID();
        store.createPending(new io.casehub.clinical.api.SponsorNotificationRequest(
                UUID.randomUUID(), UUID.randomUUID(), deviationId,
                "CONSENT_DEVIATION", DeviationSeverity.MAJOR, PiApprovalStatus.ESCALATED,
                "dr-smith@v1", "Dr. Smith", connectorId, "https://hooks.slack.com/test", "test-tenant"));
        return findByDeviationId(deviationId);
    }

    @Transactional
    UUID findByDeviationId(final UUID deviationId) {
        return SponsorNotification
                .<SponsorNotification>find("deviationId", deviationId)
                .firstResult()
                .id;
    }

    @Transactional
    SponsorNotification load(final UUID id) {
        return SponsorNotification.findById(id);
    }

    void stubPolicy(final SponsorNotificationRetryPolicy policy) {
        final Map<String, Object> values = new HashMap<>();
        final StringBuilder sb = new StringBuilder()
                .append(policy.maxAttempts()).append(',')
                .append(policy.retryInterval().toMinutes()).append(',')
                .append(policy.backoffMultiplier());
        if (policy.maxInterval() != null) {
            sb.append(',').append(policy.maxInterval().toMinutes());
        }
        values.put(SponsorNotificationRetryPolicy.KEY.qualifiedName(), sb.toString());
        final Preferences prefs = new MapPreferences(values);
        when(preferenceProvider.resolve(any(SettingsScope.class))).thenReturn(prefs);
    }

    // ── exponential backoff ───────────────────────────────────────────────────

    @Test
    void exponential_backoff_doubles_delay_on_second_failure() {
        // attempt 1: delay = 15m * 2^0 = 15m
        // attempt 2: delay = 15m * 2^1 = 30m
        final UUID id = createPending("slack");
        slackConnector.setShouldThrow(true);
        stubPolicy(EXPONENTIAL);

        delivery.attemptDelivery(id);   // attempt 1 → FAILED, nextRetryAfter = now + 15m
        assertThat(load(id).nextRetryAfter).isEqualTo(FIXED.plus(Duration.ofMinutes(15)));

        // Simulate second attempt (reset attempt count in entity to 1 so service sees attemptNumber=2)
        setAttempts(id, 1);
        delivery.attemptDelivery(id);   // attempt 2 → FAILED, nextRetryAfter = now + 30m
        assertThat(load(id).nextRetryAfter).isEqualTo(FIXED.plus(Duration.ofMinutes(30)));
    }

    @Test
    void multiplier_1_0_produces_fixed_interval_regardless_of_attempt_number() {
        final UUID id = createPending("slack");
        slackConnector.setShouldThrow(true);
        stubPolicy(THREE_ATTEMPTS);   // multiplier=1.0

        delivery.attemptDelivery(id);   // attempt 1 → delay = 30m * 1.0^0 = 30m
        assertThat(load(id).nextRetryAfter).isEqualTo(FIXED.plus(Duration.ofMinutes(30)));

        setAttempts(id, 1);
        delivery.attemptDelivery(id);   // attempt 2 → delay = 30m * 1.0^1 = 30m (fixed)
        assertThat(load(id).nextRetryAfter).isEqualTo(FIXED.plus(Duration.ofMinutes(30)));
    }

    @Test
    void max_interval_caps_exponential_growth() {
        // 15m * 2^3 = 120m > cap of 60m → should be capped at 60m
        final UUID id = createPending("slack");
        slackConnector.setShouldThrow(true);
        stubPolicy(EXPONENTIAL_CAPPED);

        setAttempts(id, 3);   // simulate we're on attempt 4: delay = 15m * 2^3 = 120m > 60m cap
        delivery.attemptDelivery(id);
        assertThat(load(id).nextRetryAfter).isEqualTo(FIXED.plus(Duration.ofMinutes(60)));
    }

    @Transactional
    void setAttempts(final UUID id, final int attempts) {
        final SponsorNotification n = SponsorNotification.findById(id);
        n.attempts = attempts;
        n.status = io.casehub.clinical.api.model.SponsorNotificationStatus.FAILED;
        n.nextRetryAfter = FIXED.minusSeconds(1); // mark eligible for retry
    }
}
