package io.casehub.clinical.service;

import io.casehub.clinical.api.model.RegulatorySubmissionStatus;
import io.casehub.clinical.entity.AdverseEvent;
import io.casehub.work.api.WorkItemLifecycleEvent;
import io.casehub.work.api.WorkItem;
import io.casehub.work.api.WorkItemStatus;
import io.casehub.work.engine.CallerRef;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.UUID;
import org.jboss.logging.Logger;

/**
 * Sets ae.regulatorySubmissionStatus = DEADLINE_MISSED when a regulatory submission WorkItem
 * reaches ESCALATED terminal status (all breach tiers exhausted).
 *
 * <p>Pattern matches IrbDecisionListener:
 * - instanceof WorkItem guard (null-safe, type-safe, wire-reconstructed events handled)
 * - CallerRef.parse() for callerRef extraction (existing sealed interface API)
 * - != PENDING idempotency guard
 *
 * <p>ExpiryLifecycleService.fireLifecycleEvent("ESCALATED", item) fires with detail=null —
 * the Exhausted(reason) string goes to the audit log only.
 */
@ApplicationScoped
public class RegulatorySubmissionBreachListener {

    private static final Logger LOG = Logger.getLogger(RegulatorySubmissionBreachListener.class);

    @Inject RegulatorySubmissionLedgerWriter ledgerWriter;

    public void onWorkItemLifecycle(@ObservesAsync WorkItemLifecycleEvent event) {
        if (event.status() != WorkItemStatus.ESCALATED) {
            return;
        }
        WorkItem workItem = event.workItem();
        if (workItem == null) {
            return;
        }
        CallerRef ref = CallerRef.parse(workItem.callerRef());
        if (ref == null) {
            return;
        }
        markDeadlineMissed(ref.caseId());
    }

    @Transactional
    void markDeadlineMissed(UUID caseId) {
        AdverseEvent ae = AdverseEvent.find("regulatorySubmissionCaseId", caseId).firstResult();
        if (ae == null) {
            return;
        }
        if (ae.regulatorySubmissionStatus != RegulatorySubmissionStatus.PENDING) {
            LOG.debugf("RegulatorySubmissionBreachListener: caseId=%s status=%s — skipping (not PENDING)",
                    caseId, ae.regulatorySubmissionStatus);
            return;
        }
        ae.regulatorySubmissionStatus = RegulatorySubmissionStatus.DEADLINE_MISSED;
        ledgerWriter.writeBreachEntry(ae);
        LOG.infof("RegulatorySubmissionBreachListener: aeId=%s set DEADLINE_MISSED", ae.id);
    }
}
