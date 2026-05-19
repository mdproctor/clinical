package io.casehub.clinical.service;

import io.casehub.clinical.api.ProtocolDeviationResolvedEvent;
import io.casehub.clinical.api.model.EscalationRequirement;
import io.casehub.clinical.api.model.PiApprovalStatus;
import io.casehub.clinical.entity.ProtocolDeviation;
import io.casehub.ledger.api.model.ActorType;
import io.casehub.qhorus.runtime.message.CommitmentService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Handles per-deviation expiration with independent transaction isolation.
 *
 * <p>{@link #findOverdueIds()} reads all COMMANDED deviations past their deadline in a
 * short REQUIRED transaction. {@link #expireOne(UUID)} processes each deviation in a
 * dedicated REQUIRES_NEW transaction — if that deviation's writes fail, only its
 * sub-transaction rolls back; other already-committed expirations are unaffected.
 */
@ApplicationScoped
public class DeviationExpirer {

    @Inject CommitmentService commitmentService;
    @Inject Event<ProtocolDeviationResolvedEvent> resolvedEvent;
    @Inject DeviationLedgerWriter ledgerWriter;

    @Transactional
    public List<UUID> findOverdueIds() {
        return ProtocolDeviation
            .find("piApprovalStatus = ?1 and responseDeadline < ?2",
                  PiApprovalStatus.COMMANDED, Instant.now())
            .<ProtocolDeviation>list()
            .stream().map(d -> d.id).toList();
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void expireOne(UUID deviationId) {
        ProtocolDeviation d = ProtocolDeviation.findById(deviationId);
        if (d == null || d.piApprovalStatus != PiApprovalStatus.COMMANDED) return;

        d.piApprovalStatus = PiApprovalStatus.EXPIRED;
        commitmentService.fail(d.id.toString());
        ledgerWriter.writeResolutionEntry(d, PiApprovalStatus.EXPIRED,
            "system", ActorType.SYSTEM, "deviation-expiration-job");
        resolvedEvent.fireAsync(new ProtocolDeviationResolvedEvent(
            d.id, d.siteId, d.severity,
            d.escalationRequirement != null ? d.escalationRequirement : EscalationRequirement.NONE,
            PiApprovalStatus.EXPIRED
        ));
    }
}
