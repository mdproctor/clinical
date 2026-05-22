package io.casehub.clinical.service;

import io.casehub.clinical.api.AeEscalationCompletedEvent;
import io.casehub.clinical.api.model.CtcaeGrade;
import io.casehub.engine.internal.event.CaseLifecycleEvent;
import io.casehub.engine.spi.CaseInstanceRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.jboss.logging.Logger;

/**
 * Observes case lifecycle events and handles AE escalation case completion.
 *
 * <p>Discriminates AE escalation cases by presence of {@code aeId} in the case
 * context (set at case start by AeEscalationCaseService). Deviation review cases
 * and other cases lack this key and are silently ignored.
 *
 * <p>Note: {@code CaseLifecycleEvent} is from {@code io.casehub.engine.internal.event}
 * — an internal package. Tracked as casehubio/clinical#28 to promote to a public SPI.
 */
@ApplicationScoped
public class AeEscalationListener {

    private static final Logger LOG = Logger.getLogger(AeEscalationListener.class);
    private static final Duration LOOKUP_TIMEOUT = Duration.ofSeconds(5);

    @Inject CaseInstanceRepository caseInstanceRepository;
    @Inject AeEscalationLedgerWriter ledgerWriter;
    @Inject Event<AeEscalationCompletedEvent> completedEvents;

    @Transactional
    public void onCaseLifecycle(@ObservesAsync CaseLifecycleEvent event) {
        if (!"CaseCompleted".equals(event.eventType())) return;

        var instance = caseInstanceRepository
                .findByUuid(event.caseId())
                .await().atMost(LOOKUP_TIMEOUT);
        if (instance == null) return;

        Object aeIdObj = instance.getCaseContext().getPath("aeId");
        if (aeIdObj == null) return; // not an AE escalation case

        UUID aeId;
        try {
            aeId = UUID.fromString(aeIdObj.toString());
        } catch (IllegalArgumentException e) {
            LOG.warnf("AeEscalationListener: invalid aeId in case context: %s", aeIdObj);
            return;
        }

        UUID enrollmentId = resolveUuid(instance.getCaseContext().getPath("enrollmentId"));
        if (enrollmentId == null) {
            LOG.warnf("AeEscalationListener: enrollmentId missing from case context for aeId=%s — ledger write skipped", aeId);
            return;
        }

        CtcaeGrade grade = resolveGrade(instance.getCaseContext().getPath("grade"));
        // safetyReview is the full WorkItem resolution mapped by outputMapping: "{ safetyReview: . }"
        // The resolution body must include an "outcome" field — e.g. {"outcome":"REVIEWED","reviewedAt":"..."}
        String safetyReviewOutcome = resolveOutcome(instance.getCaseContext().getPath("safetyReview"));
        boolean dsmbEscalated = instance.getCaseContext().getPath("dsmbEscalation") != null;
        Instant completedAt = Instant.now();

        ledgerWriter.writeCompletionEntry(aeId, enrollmentId, grade, safetyReviewOutcome, dsmbEscalated, completedAt);

        completedEvents.fireAsync(new AeEscalationCompletedEvent(
                aeId, grade, safetyReviewOutcome, dsmbEscalated, completedAt));
    }

    private UUID resolveUuid(Object obj) {
        if (obj == null) return null;
        try { return UUID.fromString(obj.toString()); } catch (IllegalArgumentException e) { return null; }
    }

    private CtcaeGrade resolveGrade(Object obj) {
        if (obj == null) return null;
        try { return CtcaeGrade.valueOf(obj.toString()); } catch (IllegalArgumentException e) { return null; }
    }

    private String resolveOutcome(Object obj) {
        if (!(obj instanceof Map<?, ?> map)) return null;
        Object outcome = map.get("outcome");
        return outcome != null ? outcome.toString() : null;
    }
}
