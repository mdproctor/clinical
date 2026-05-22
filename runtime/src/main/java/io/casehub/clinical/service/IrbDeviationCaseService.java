package io.casehub.clinical.service;

import io.casehub.clinical.api.ProtocolDeviationResolvedEvent;
import io.casehub.clinical.api.model.EscalationRequirement;
import io.casehub.clinical.api.model.IrbDecision;
import io.casehub.clinical.api.model.PiApprovalStatus;
import io.casehub.clinical.entity.IrbApproval;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Observes ProtocolDeviationResolvedEvent and starts an IRB deviation review engine case
 * when the deviation requires IRB review and the PI has approved it.
 *
 * <p>Creates an IrbApproval(PENDING) before starting the case so IrbDecisionListener
 * can find it by deviationId when the IRB WorkItem terminates.
 */
@ApplicationScoped
public class IrbDeviationCaseService {

    @Inject ClinicalDeviationCaseHub caseHub;

    @Transactional
    public void onDeviationResolved(@ObservesAsync ProtocolDeviationResolvedEvent event) {
        if (event.escalationRequirement() != EscalationRequirement.IRB_REVIEW) return;
        if (event.terminalStatus() != PiApprovalStatus.APPROVED) return;

        var approval = new IrbApproval();
        approval.id = UUID.randomUUID();
        approval.siteId = event.siteId();
        approval.deviationId = event.deviationId();
        approval.reviewType = "PROTOCOL_DEVIATION";
        approval.committeeId = "irb-committee";
        approval.decisionDeadline = Instant.now().plus(Duration.ofHours(72));
        approval.decision = IrbDecision.PENDING;
        approval.persist();

        var initialContext = Map.<String, Object>of(
                "deviationId", event.deviationId().toString(),
                "siteId", event.siteId().toString(),
                "severity", event.severity().name(),
                "escalationRequirement", event.escalationRequirement().name(),
                "irbConsultationRequired", true);

        caseHub.startCase(initialContext);
    }
}
