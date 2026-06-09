package io.casehub.clinical.service;

import io.casehub.clinical.entity.SponsorNotification;
import io.casehub.clinical.api.ClinicalActors;
import io.casehub.clinical.ledger.SponsorNotificationLedgerEntry;
import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.runtime.model.LedgerEntry;
import io.casehub.ledger.runtime.repository.LedgerEntryRepository;
import io.casehub.platform.api.identity.ActorType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Writes per-attempt sponsor notification ledger entries to the tamper-evident audit chain.
 *
 * <p>Subject is {@code notificationId} (not {@code deviationId}) — the notification entity is the
 * audit subject. The per-attempt chain is isolated from the deviation's lifecycle chain, supporting
 * independent GDPR erasure. {@code deviationId} is stored for cross-reference traversal.
 *
 * <p>Actor roles follow the observer-failure-actor-role-naming convention:
 * <ul>
 *   <li>{@code "sponsor-notifier"} — successful delivery</li>
 *   <li>{@code "sponsor-notifier-attempt-failed"} — attempt failed, retries remain</li>
 *   <li>{@code "sponsor-notifier-exhausted"} — all attempts consumed</li>
 * </ul>
 */
@ApplicationScoped
public class SponsorNotificationLedgerWriter {

    @Inject LedgerEntryRepository repo;
    @Inject Clock clock;

    /**
     * Records a successful delivery attempt.
     *
     * <p>{@code deliveredAt} is caller-supplied (connector acknowledgement time) — not
     * {@code clock.instant()} — so both audit chains record the same timestamp for the same
     * delivery event (same pattern as {@code DeviationLedgerWriter.writeSponsorNotifiedEntry}).
     */
    @Transactional
    public void writeDelivered(final SponsorNotification n, final int attemptNumber,
                               final Instant deliveredAt) {
        final SponsorNotificationLedgerEntry entry = base(n, attemptNumber);
        entry.actorRole = "sponsor-notifier";
        entry.delivered = true;
        entry.occurredAt = deliveredAt;
        repo.save(entry, "default");
    }

    /** Records a failed delivery attempt where retries remain. */
    @Transactional
    public void writeFailed(final SponsorNotification n, final int attemptNumber,
                            final String reason) {
        final SponsorNotificationLedgerEntry entry = base(n, attemptNumber);
        entry.actorRole = "sponsor-notifier-attempt-failed";
        entry.delivered = false;
        entry.failureReason = reason;
        entry.occurredAt = clock.instant();
        repo.save(entry, "default");
    }

    /** Records terminal exhaustion — all attempts consumed, sponsor unreached. */
    @Transactional
    public void writeExhausted(final SponsorNotification n, final int attemptNumber,
                               final String reason) {
        final SponsorNotificationLedgerEntry entry = base(n, attemptNumber);
        entry.actorRole = "sponsor-notifier-exhausted";
        entry.delivered = false;
        entry.failureReason = reason;
        entry.occurredAt = clock.instant();
        repo.save(entry, "default");
    }

    private SponsorNotificationLedgerEntry base(final SponsorNotification n,
                                                 final int attemptNumber) {
        final SponsorNotificationLedgerEntry entry = new SponsorNotificationLedgerEntry();
        entry.id = UUID.randomUUID();
        entry.subjectId = n.id;
        entry.sequenceNumber = nextSequenceNumber(n.id);
        entry.actorId = ClinicalActors.CLINICAL_SERVICE;
        entry.actorType = ActorType.SYSTEM;
        entry.entryType = LedgerEntryType.EVENT;
        entry.notificationId = n.id;
        entry.deviationId = n.deviationId;
        entry.attemptNumber = attemptNumber;
        return entry;
    }

    private int nextSequenceNumber(final UUID notificationId) {
        return repo.findLatestBySubjectId(notificationId, "default")
                .map((final LedgerEntry e) -> e.sequenceNumber + 1)
                .orElse(1);
    }
}
