package io.casehub.clinical.service;

import io.casehub.clinical.api.AdverseEventReportedEvent;
import io.casehub.clinical.api.model.CtcaeGrade;
import io.casehub.clinical.api.model.RegulatorySubmissionStatus;
import io.casehub.clinical.entity.AdverseEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.jboss.logging.Logger;

/**
 * Observes AdverseEventReportedEvent concurrently with AeEscalationCaseService and
 * SusarOversightCaseService. Starts an IND expedited safety report filing case
 * when Grade 3 + unexpected (15-day, 21 CFR 312.32(c)(1)(ii)) or
 * Grade 5 + unexpected (7-day, 21 CFR 312.32(c)(1)(i)) criteria are met.
 *
 * <p>Three-phase pattern (ADR-0004): startCase().join() outside any @Transactional
 * boundary to avoid deadlocking the Agroal pool.
 *
 * <p>Phase 1 writes the RegulatorySubmissionLedgerEntry in the same transaction as
 * the status update — ledger evidence established at obligation identification time.
 */
@ApplicationScoped
public class RegulatorySubmissionCaseService {

    private static final Logger LOG = Logger.getLogger(RegulatorySubmissionCaseService.class);

    private static boolean isIndReportable(final CtcaeGrade grade) {
        return grade == CtcaeGrade.GRADE_3 || grade == CtcaeGrade.GRADE_5;
    }

    // When #82 adds GRADE_4: add case GRADE_4 -> Duration.ofDays(7)
    private static Duration indReportingWindow(final CtcaeGrade grade) {
        return switch (grade) {
            case GRADE_5 -> Duration.ofDays(7);
            case GRADE_3 -> Duration.ofDays(15);
            default -> throw new IllegalArgumentException("no IND reporting window for grade: " + grade);
        };
    }

    @Inject ClinicalRegulatorySubmissionCaseHub regulatorySubmissionCaseHub;
    @Inject RegulatorySubmissionLedgerWriter ledgerWriter;

    public void onAdverseEventReported(@ObservesAsync AdverseEventReportedEvent event) {
        try {
            Map<String, Object> initialContext = prepareAndMark(event);
            if (initialContext == null) return;
            UUID caseId = regulatorySubmissionCaseHub.startCase(initialContext).toCompletableFuture().join();
            persistCaseId(event.aeId(), caseId);
        } catch (Exception e) {
            LOG.errorf(e, "RegulatorySubmissionCaseService: case start failed for aeId=%s", event.aeId());
            try {
                resetToNone(event.aeId());
            } catch (Exception ex) {
                LOG.errorf(ex, "RegulatorySubmissionCaseService: reset failed for aeId=%s", event.aeId());
            }
        }
    }

    @Transactional
    Map<String, Object> prepareAndMark(AdverseEventReportedEvent event) {
        AdverseEvent ae = AdverseEvent.findById(event.aeId());
        if (ae == null) {
            LOG.warnf("RegulatorySubmissionCaseService: AE not found for aeId=%s — skipping", event.aeId());
            return null;
        }
        // Grade 3 (15-day, §(c)(1)(ii)) and Grade 5 (7-day, §(c)(1)(i)) + unexpected trigger IND expedited safety reporting
        if (!isIndReportable(ae.grade) || !ae.unexpected) {
            return null;
        }
        // Idempotency guard — protects against CDI at-least-once re-delivery
        if (ae.regulatorySubmissionStatus != RegulatorySubmissionStatus.NONE) {
            LOG.debugf("RegulatorySubmissionCaseService: aeId=%s already processed (status=%s) — skipping",
                    event.aeId(), ae.regulatorySubmissionStatus);
            return null;
        }
        ae.regulatorySubmissionStatus = RegulatorySubmissionStatus.PENDING;
        // Ledger write in same TX — evidence established at obligation identification time
        ledgerWriter.writeEntry(ae);
        return Map.of(
                "aeId", ae.id.toString(),
                "grade", ae.grade.name(),
                "unexpected", ae.unexpected,
                "siteId", event.siteId().toString(),
                "tenantId", ae.tenantId,
                "indReportingDeadline", ae.reportedAt.plus(indReportingWindow(ae.grade)).toString());
    }

    @Transactional
    void persistCaseId(UUID aeId, UUID caseId) {
        AdverseEvent ae = AdverseEvent.findById(aeId);
        if (ae == null) {
            LOG.warnf("RegulatorySubmissionCaseService: AE not found in Phase 3 for aeId=%s", aeId);
            return;
        }
        ae.regulatorySubmissionCaseId = caseId;
    }

    @Transactional
    void resetToNone(UUID aeId) {
        AdverseEvent ae = AdverseEvent.findById(aeId);
        if (ae == null) return;
        ae.regulatorySubmissionStatus = RegulatorySubmissionStatus.NONE; // allow retry
    }
}
