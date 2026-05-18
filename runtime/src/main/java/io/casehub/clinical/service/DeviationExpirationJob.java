package io.casehub.clinical.service;

import io.casehub.clinical.api.ProtocolDeviationResolvedEvent;
import io.casehub.clinical.api.model.EscalationRequirement;
import io.casehub.clinical.api.model.PiApprovalStatus;
import io.casehub.clinical.entity.ProtocolDeviation;
import io.casehub.ledger.api.model.ActorType;
import io.casehub.qhorus.runtime.message.CommitmentService;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Hourly scan that marks COMMANDED deviations whose responseDeadline has passed as EXPIRED.
 *
 * <p>Per GCP/ICH E6(R3): a PI who does not respond within the required window is treated
 * as a protocol failure — the deviation cannot be self-resolved. Expiry:
 * <ul>
 *   <li>Transitions {@code piApprovalStatus} to {@link PiApprovalStatus#EXPIRED}</li>
 *   <li>Calls {@link CommitmentService#fail(String)} to close the open Commitment in Qhorus</li>
 *   <li>Writes an EXPIRED ledger entry via {@link DeviationLedgerWriter} for FDA audit trail</li>
 *   <li>Fires a {@link ProtocolDeviationResolvedEvent} so downstream consumers (IRB notification,
 *       sponsor alert) can react to the terminal state</li>
 * </ul>
 *
 * <p>The scheduler is disabled in tests ({@code quarkus.scheduler.enabled=false}).
 * Call {@link #checkExpiredCommitments()} directly in tests.
 */
@ApplicationScoped
public class DeviationExpirationJob {

    @Inject CommitmentService commitmentService;
    @Inject Event<ProtocolDeviationResolvedEvent> resolvedEvent;
    @Inject DeviationLedgerWriter ledgerWriter;

    @Scheduled(every = "${casehub.clinical.deviation.expiration-check-interval:1h}",
               identity = "deviation-expiration")
    @Transactional
    public void checkExpiredCommitments() {
        List<ProtocolDeviation> overdue = ProtocolDeviation
            .find("piApprovalStatus = ?1 and responseDeadline < ?2",
                  PiApprovalStatus.COMMANDED, Instant.now())
            .list();

        for (ProtocolDeviation d : overdue) {
            try {
                d.piApprovalStatus = PiApprovalStatus.EXPIRED;
                commitmentService.fail(d.id.toString());
                ledgerWriter.writeResolutionEntry(d, PiApprovalStatus.EXPIRED,
                    "system", ActorType.SYSTEM, "deviation-expiration-job");
                resolvedEvent.fireAsync(new ProtocolDeviationResolvedEvent(
                    d.id, d.siteId, d.severity,
                    d.escalationRequirement != null ? d.escalationRequirement : EscalationRequirement.NONE,
                    PiApprovalStatus.EXPIRED
                ));
            } catch (Exception e) {
                // Log and continue — one failure must not roll back status updates for other deviations.
                // The deviation remains COMMANDED and will be retried on the next scheduled run.
                d.piApprovalStatus = PiApprovalStatus.COMMANDED;
                org.jboss.logging.Logger.getLogger(DeviationExpirationJob.class)
                    .errorf(e, "Failed to expire deviation %s — will retry next run", d.id);
            }
        }
    }
}
