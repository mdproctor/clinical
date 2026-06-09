package io.casehub.clinical.service;

import io.casehub.clinical.api.AeEscalationCompletedEvent;
import io.casehub.clinical.api.model.CtcaeGrade;
import io.casehub.engine.common.spi.event.CaseLifecycleEvent;
import io.casehub.engine.common.spi.CaseInstanceRepository;
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
 * <p>{@code CaseLifecycleEvent} is from {@code io.casehub.engine.common.spi.event}
 * — the public SPI package promoted in engine#378 (was internal.event).
 */
@ApplicationScoped
public class AeEscalationListener {

    private static final Logger LOG = Logger.getLogger(AeEscalationListener.class);
    private static final Duration LOOKUP_TIMEOUT = Duration.ofSeconds(5);
    /** Key written by the AE escalation YAML binding's outputMapping: "{ safetyReview: . }". */
    static final String OUTCOME_KEY = "outcome";

    @Inject CaseInstanceRepository caseInstanceRepository;
    @Inject AeEscalationLedgerWriter ledgerWriter;
    @Inject AeStatusUpdater statusUpdater;
    @Inject Event<AeEscalationCompletedEvent> completedEvents;
    @Inject io.casehub.clinical.memory.ClinicalMemoryService memoryService;

    @Transactional
    public void onCaseLifecycle(@ObservesAsync CaseLifecycleEvent event) {
        LOG.debugf("AeEscalationListener: received eventType=%s caseStatus=%s caseId=%s", event.eventType(), event.caseStatus(), event.caseId());
        if (!"GoalReached".equals(event.eventType()) && !"CaseCompleted".equals(event.eventType())) return;

        var instance = caseInstanceRepository
                .findByUuid(event.caseId(), event.tenancyId())
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

        // REQUIRES_NEW: commits independently of the outer transaction.
        // Returns false if already COMPLETED — GoalReached fires multiple times per case (idempotency guard).
        boolean firstCompletion = statusUpdater.markCompleted(aeId);
        if (!firstCompletion) return;

        // Context resolution outside try block — if these throw, no REQUIRES_NEW has committed,
        // so there is no FDA gap. Exceptions propagate to the @ObservesAsync dispatcher, which logs them.
        UUID enrollmentId = resolveUuid(instance.getCaseContext().getPath("enrollmentId"));
        if (enrollmentId == null) {
            LOG.warnf("AeEscalationListener: enrollmentId missing from case context for aeId=%s — ledger write skipped", aeId);
            return;
        }
        UUID siteId = resolveUuid(instance.getCaseContext().getPath("siteId"));
        CtcaeGrade grade = resolveGrade(instance.getCaseContext().getPath("grade"));
        String safetyReviewOutcome = resolveOutcome(instance.getCaseContext().getPath("safetyReview"));
        boolean dsmbEscalated = instance.getCaseContext().getPath("dsmbEscalation") != null;
        Instant completedAt = Instant.now();

        // Narrow try/catch: markCompleted committed (REQUIRES_NEW). Any exception here is an FDA gap.
        // ledgerWritten guards against a spurious failure entry when only fireAsync throws after success.
        boolean ledgerWritten = false;
        try {
            ledgerWriter.writeCompletionEntry(aeId, enrollmentId, grade, safetyReviewOutcome, dsmbEscalated, completedAt);
            ledgerWritten = true;
            String tenantId = resolveString(instance.getCaseContext().getPath("tenantId"));
            if (tenantId != null) {
                memoryService.storeAeOutcome(aeId, enrollmentId, grade, safetyReviewOutcome, dsmbEscalated, tenantId);
            }
            completedEvents.fireAsync(new AeEscalationCompletedEvent(
                    aeId, grade, siteId, safetyReviewOutcome, dsmbEscalated, completedAt));
        } catch (Exception e) {
            if (!ledgerWritten) {
                LOG.errorf(e, "AeEscalationListener: unexpected error for aeId=%s (enrollmentId=%s, grade=%s) — writing failure entry", aeId, enrollmentId, grade);
                try {
                    ledgerWriter.writeObserverFailureEntry(aeId, enrollmentId, grade);
                } catch (Exception writeEx) {
                    LOG.errorf(writeEx, "AUDIT GAP: could not write observer failure entry for aeId=%s", aeId);
                }
            } else {
                LOG.errorf(e, "AeEscalationListener: downstream fireAsync failed for aeId=%s — ledger entry exists, no fallback needed", aeId);
            }
        }
    }

    private String resolveString(Object obj) {
        return obj != null ? obj.toString() : null;
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
        Object outcome = map.get(OUTCOME_KEY);
        return outcome != null ? outcome.toString() : null;
    }
}
