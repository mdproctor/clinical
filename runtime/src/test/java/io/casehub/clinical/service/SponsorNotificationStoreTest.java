package io.casehub.clinical.service;

import io.casehub.clinical.api.SponsorNotificationRequest;
import io.casehub.clinical.api.model.DeviationSeverity;
import io.casehub.clinical.api.model.PiApprovalStatus;
import io.casehub.clinical.api.model.SponsorNotificationStatus;
import io.casehub.clinical.entity.SponsorNotification;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@QuarkusTest
class SponsorNotificationStoreTest {

    @Inject SponsorNotificationStore store;
    @InjectMock Clock clock;
    @InjectMock SponsorNotificationLedgerWriter ledgerWriter;  // suppress ledger writes

    private static final Instant FIXED = Instant.parse("2026-06-05T10:00:00Z");
    private SponsorNotificationRequest request;
    private UUID deviationId;
    private UUID trialId;
    private UUID siteId;

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenReturn(FIXED);
        deviationId = UUID.randomUUID();
        trialId = UUID.randomUUID();
        siteId = UUID.randomUUID();
        request = new SponsorNotificationRequest(
                trialId, siteId, deviationId, "CONSENT_DEVIATION",
                DeviationSeverity.MAJOR, PiApprovalStatus.ESCALATED,
                "dr-smith@v1", "Dr. Smith", "slack", "https://hooks.slack.com/test", "test-tenant");
    }

    // ── createPending ─────────────────────────────────────────────────────────

    @Test
    void createPending_persists_entity_with_correct_fields() {
        store.createPending(request);

        final SponsorNotification n = loadByDeviationId(deviationId);
        assertThat(n).isNotNull();
        assertThat(n.status).isEqualTo(SponsorNotificationStatus.PENDING);
        assertThat(n.attempts).isEqualTo(0);
        assertThat(n.deviationId).isEqualTo(deviationId);
        assertThat(n.trialId).isEqualTo(trialId);
        assertThat(n.siteId).isEqualTo(siteId);
        assertThat(n.createdAt).isEqualTo(FIXED);
        assertThat(n.connectorId).isEqualTo("slack");
        assertThat(n.destination).isEqualTo("https://hooks.slack.com/test");
        assertThat(n.piId).isEqualTo("dr-smith@v1");
        assertThat(n.piDisplayName).isEqualTo("Dr. Smith");
        assertThat(n.severity).isEqualTo(DeviationSeverity.MAJOR);
        assertThat(n.terminalStatus).isEqualTo(PiApprovalStatus.ESCALATED);
        assertThat(n.deviationType).isEqualTo("CONSENT_DEVIATION");
        assertThat(n.nextRetryAfter).isNull();
        assertThat(n.deliveredAt).isNull();
        assertThat(n.failureReason).isNull();
        assertThat(n.lastAttemptedAt).isNull();
    }

    @Test
    void createPending_rejects_approved_terminalStatus() {
        final SponsorNotificationRequest bad = requestWithStatus(PiApprovalStatus.APPROVED);
        assertThatThrownBy(() -> store.createPending(bad))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("APPROVED");
    }

    @Test
    void createPending_rejects_commanded_terminalStatus() {
        final SponsorNotificationRequest bad = requestWithStatus(PiApprovalStatus.COMMANDED);
        assertThatThrownBy(() -> store.createPending(bad))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("COMMANDED");
    }

    @Test
    void createPending_rejects_pending_terminalStatus() {
        final SponsorNotificationRequest bad = requestWithStatus(PiApprovalStatus.PENDING);
        assertThatThrownBy(() -> store.createPending(bad))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PENDING");
    }

    @Test
    void createPending_accepts_rejected_terminalStatus() {
        store.createPending(requestWithStatus(PiApprovalStatus.REJECTED));
        final SponsorNotification n = loadByDeviationId(deviationId);
        assertThat(n.terminalStatus).isEqualTo(PiApprovalStatus.REJECTED);
    }

    @Test
    void createPending_accepts_expired_terminalStatus() {
        store.createPending(requestWithStatus(PiApprovalStatus.EXPIRED));
        final SponsorNotification n = loadByDeviationId(deviationId);
        assertThat(n.terminalStatus).isEqualTo(PiApprovalStatus.EXPIRED);
    }

    @Test
    void createPending_generates_unique_id() {
        store.createPending(request);
        store.createPending(request);

        final List<SponsorNotification> all = loadAll();
        final List<UUID> ids = all.stream().map(n -> n.id).toList();
        assertThat(ids).doesNotHaveDuplicates();
    }

    // ── findEligibleIds ───────────────────────────────────────────────────────

    @Test
    void findEligibleIds_returns_pending_with_null_nextRetryAfter() {
        store.createPending(request);
        final UUID id = loadByDeviationId(deviationId).id;

        final List<UUID> ids = store.findEligibleIds(FIXED, 100);

        assertThat(ids).contains(id);
    }

    @Test
    void findEligibleIds_returns_failed_when_nextRetryAfter_in_past() {
        store.createPending(request);
        final SponsorNotification n = loadByDeviationId(deviationId);
        setFailedWithNextRetry(n.id, FIXED.minusSeconds(1));

        final List<UUID> ids = store.findEligibleIds(FIXED, 100);

        assertThat(ids).contains(n.id);
    }

    @Test
    void findEligibleIds_excludes_failed_when_nextRetryAfter_in_future() {
        store.createPending(request);
        final SponsorNotification n = loadByDeviationId(deviationId);
        setFailedWithNextRetry(n.id, FIXED.plusSeconds(60));

        final List<UUID> ids = store.findEligibleIds(FIXED, 100);

        assertThat(ids).doesNotContain(n.id);
    }

    @Test
    void findEligibleIds_excludes_delivered() {
        store.createPending(request);
        final SponsorNotification n = loadByDeviationId(deviationId);
        setStatus(n.id, SponsorNotificationStatus.DELIVERED);

        final List<UUID> ids = store.findEligibleIds(FIXED, 100);

        assertThat(ids).doesNotContain(n.id);
    }

    @Test
    void findEligibleIds_excludes_exhausted() {
        store.createPending(request);
        final SponsorNotification n = loadByDeviationId(deviationId);
        setStatus(n.id, SponsorNotificationStatus.EXHAUSTED);

        final List<UUID> ids = store.findEligibleIds(FIXED, 100);

        assertThat(ids).doesNotContain(n.id);
    }

    @Test
    void findEligibleIds_respects_batch_limit() {
        for (int i = 0; i < 5; i++) {
            final UUID extra = UUID.randomUUID();
            store.createPending(new SponsorNotificationRequest(
                    UUID.randomUUID(), UUID.randomUUID(), extra, "TYPE",
                    DeviationSeverity.MINOR, PiApprovalStatus.ESCALATED,
                    null, null, "slack", "https://dest", "test-tenant"));
        }

        final List<UUID> ids = store.findEligibleIds(FIXED, 2);

        assertThat(ids).hasSizeLessThanOrEqualTo(2);
    }

    // ── markDelivered ─────────────────────────────────────────────────────────

    @Test
    void markDelivered_sets_delivered_status_and_timestamps() {
        store.createPending(request);
        final SponsorNotification n = loadByDeviationId(deviationId);
        final Instant deliveredAt = FIXED.plusSeconds(10);

        store.markDelivered(n.id, n, 1, deliveredAt);

        final SponsorNotification updated = load(n.id);
        assertThat(updated.status).isEqualTo(SponsorNotificationStatus.DELIVERED);
        assertThat(updated.attempts).isEqualTo(1);
        assertThat(updated.deliveredAt).isEqualTo(deliveredAt);
        assertThat(updated.lastAttemptedAt).isEqualTo(deliveredAt);
    }

    // ── markFailed ────────────────────────────────────────────────────────────

    @Test
    void markFailed_sets_failed_status_nextRetryAfter_and_reason() {
        store.createPending(request);
        final SponsorNotification n = loadByDeviationId(deviationId);
        final Instant nextRetry = FIXED.plusSeconds(1800);

        store.markFailed(n.id, n, "Connector timeout", 1, nextRetry);

        final SponsorNotification updated = load(n.id);
        assertThat(updated.status).isEqualTo(SponsorNotificationStatus.FAILED);
        assertThat(updated.attempts).isEqualTo(1);
        assertThat(updated.failureReason).isEqualTo("Connector timeout");
        assertThat(updated.nextRetryAfter).isEqualTo(nextRetry);
        assertThat(updated.lastAttemptedAt).isEqualTo(FIXED);
    }

    // ── markExhausted ─────────────────────────────────────────────────────────

    @Test
    void markExhausted_sets_exhausted_status() {
        store.createPending(request);
        final SponsorNotification n = loadByDeviationId(deviationId);

        store.markExhausted(n.id, n, "All retries consumed", 3);

        final SponsorNotification updated = load(n.id);
        assertThat(updated.status).isEqualTo(SponsorNotificationStatus.EXHAUSTED);
        assertThat(updated.attempts).isEqualTo(3);
        assertThat(updated.failureReason).isEqualTo("All retries consumed");
        assertThat(updated.lastAttemptedAt).isEqualTo(FIXED);
        assertThat(updated.deliveredAt).isNull();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private SponsorNotificationRequest requestWithStatus(final PiApprovalStatus status) {
        return new SponsorNotificationRequest(
                trialId, siteId, deviationId, "CONSENT_DEVIATION",
                DeviationSeverity.MAJOR, status,
                "dr-smith@v1", "Dr. Smith", "slack", "https://hooks.slack.com/test", "test-tenant");
    }

    @Transactional
    SponsorNotification loadByDeviationId(final UUID devId) {
        return SponsorNotification.<SponsorNotification>find("deviationId", devId)
                .firstResult();
    }

    @Transactional
    SponsorNotification load(final UUID id) {
        return SponsorNotification.findById(id);
    }

    @Transactional
    List<SponsorNotification> loadAll() {
        return SponsorNotification.listAll();
    }

    @Transactional
    void setStatus(final UUID id, final SponsorNotificationStatus status) {
        final SponsorNotification n = SponsorNotification.findById(id);
        n.status = status;
    }

    @Transactional
    void setFailedWithNextRetry(final UUID id, final Instant nextRetryAfter) {
        final SponsorNotification n = SponsorNotification.findById(id);
        n.status = SponsorNotificationStatus.FAILED;
        n.nextRetryAfter = nextRetryAfter;
    }
}
