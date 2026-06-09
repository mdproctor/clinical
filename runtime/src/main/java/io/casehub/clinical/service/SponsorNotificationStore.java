package io.casehub.clinical.service;

import io.casehub.clinical.api.SponsorNotificationRequest;
import io.casehub.clinical.api.model.PiApprovalStatus;
import io.casehub.clinical.api.model.SponsorNotificationStatus;
import io.casehub.clinical.entity.SponsorNotification;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Owns all {@link SponsorNotification} entity mutations.
 *
 * <p>Outcome-recording methods ({@code markDelivered}, {@code markFailed}, {@code markExhausted})
 * also call {@link SponsorNotificationLedgerWriter} internally — entity update and notification
 * ledger write commit atomically in the same {@code REQUIRES_NEW} transaction.
 *
 * <p>Package-private: all callers ({@link DurableSponsorNotifier},
 * {@link SponsorNotificationDeliveryService}, {@link SponsorNotificationRetryJob}) must remain
 * in {@code io.casehub.clinical.service}.
 */
@ApplicationScoped
class SponsorNotificationStore {

    private static final Set<PiApprovalStatus> VALID_TERMINAL_STATUSES =
            Set.of(PiApprovalStatus.ESCALATED, PiApprovalStatus.REJECTED, PiApprovalStatus.EXPIRED);

    @Inject SponsorNotificationLedgerWriter ledgerWriter;
    @Inject Clock clock;

    /** Creates a PENDING entity. Commits in its own REQUIRES_NEW so it survives listener rollback. */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    void createPending(final SponsorNotificationRequest req) {
        if (!VALID_TERMINAL_STATUSES.contains(req.terminalStatus())) {
            throw new IllegalArgumentException(
                    "Invalid terminalStatus for sponsor notification: " + req.terminalStatus()
                    + " — must be one of ESCALATED, REJECTED, EXPIRED");
        }
        final SponsorNotification n = new SponsorNotification();
        n.id = UUID.randomUUID();
        n.tenantId = req.tenantId();
        n.deviationId = req.deviationId();
        n.trialId = req.trialId();
        n.siteId = req.siteId();
        n.status = SponsorNotificationStatus.PENDING;
        n.attempts = 0;
        n.createdAt = clock.instant();
        n.piId = req.piId();
        n.piDisplayName = req.piDisplayName();
        n.connectorId = req.sponsorNotificationConnectorId();
        n.destination = req.sponsorNotificationDestination();
        n.severity = req.severity();
        n.terminalStatus = req.terminalStatus();
        n.deviationType = req.deviationType();
        n.persist();
    }

    /**
     * Loads the entity in a short read transaction. Entity detaches on return.
     *
     * <p>Only scalar fields are safe after detach — this entity must never gain association
     * mappings (would throw {@code LazyInitializationException} in Phase 2).
     */
    @Transactional
    SponsorNotification load(final UUID id) {
        return SponsorNotification.findById(id);
    }

    /**
     * Returns IDs of notifications eligible for delivery: PENDING or FAILED with
     * {@code nextRetryAfter} in the past (or null).
     *
     * <p>Uses a JPQL projection to fetch only the {@code id} column — avoids loading
     * all entity fields for potentially large backlogs.
     */
    @Transactional
    List<UUID> findEligibleIds(final Instant now, final int limit) {
        return SponsorNotification.getEntityManager()
                .createQuery(
                        "SELECT n.id FROM SponsorNotification n"
                        + " WHERE n.status IN (:s1, :s2)"
                        + " AND (n.nextRetryAfter IS NULL OR n.nextRetryAfter <= :now)",
                        UUID.class)
                .setParameter("s1", SponsorNotificationStatus.PENDING)
                .setParameter("s2", SponsorNotificationStatus.FAILED)
                .setParameter("now", now)
                .setMaxResults(limit)
                .getResultList();
    }

    /**
     * Marks entity DELIVERED and writes the notification ledger entry atomically.
     * {@code deliveredAt} is the connector acknowledgement time, not clock.instant().
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    void markDelivered(final UUID id, final SponsorNotification snapshot,
                       final int attemptNumber, final Instant deliveredAt) {
        final SponsorNotification n = SponsorNotification.findById(id);
        if (n == null) return;
        n.status = SponsorNotificationStatus.DELIVERED;
        n.attempts = attemptNumber;
        n.deliveredAt = deliveredAt;
        n.lastAttemptedAt = deliveredAt;
        ledgerWriter.writeDelivered(snapshot, attemptNumber, deliveredAt);
    }

    /**
     * Marks entity FAILED and writes the notification ledger entry atomically.
     * {@code now} is the failure-detection timestamp.
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    void markFailed(final UUID id, final SponsorNotification snapshot,
                    final String reason, final int attemptNumber, final Instant nextRetry) {
        final SponsorNotification n = SponsorNotification.findById(id);
        if (n == null) return;
        // Guard: never regress a terminal state — DELIVERED or EXHAUSTED cannot go backwards
        if (n.status == SponsorNotificationStatus.DELIVERED
                || n.status == SponsorNotificationStatus.EXHAUSTED) return;
        n.status = SponsorNotificationStatus.FAILED;
        n.attempts = attemptNumber;
        n.failureReason = reason;
        n.nextRetryAfter = nextRetry;
        n.lastAttemptedAt = clock.instant();
        ledgerWriter.writeFailed(snapshot, attemptNumber, reason);
    }

    /**
     * Marks entity EXHAUSTED and writes the notification ledger entry atomically.
     * {@code now} is the failure-detection timestamp.
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    void markExhausted(final UUID id, final SponsorNotification snapshot,
                       final String reason, final int attemptNumber) {
        final SponsorNotification n = SponsorNotification.findById(id);
        if (n == null) return;
        // Guard: idempotent — EXHAUSTED cannot be re-driven backwards or re-exhausted
        if (n.status == SponsorNotificationStatus.DELIVERED
                || n.status == SponsorNotificationStatus.EXHAUSTED) return;
        n.status = SponsorNotificationStatus.EXHAUSTED;
        n.attempts = attemptNumber;
        n.failureReason = reason;
        n.lastAttemptedAt = clock.instant();
        ledgerWriter.writeExhausted(snapshot, attemptNumber, reason);
    }
}
