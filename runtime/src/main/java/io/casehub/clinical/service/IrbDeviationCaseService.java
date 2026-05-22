package io.casehub.clinical.service;

import io.casehub.clinical.api.ProtocolDeviationResolvedEvent;
import io.casehub.clinical.api.model.EscalationRequirement;
import io.casehub.clinical.api.model.PiApprovalStatus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;
import java.util.HashMap;
import java.util.Map;

/**
 * Observes ProtocolDeviationResolvedEvent and starts an IRB deviation review engine case
 * when the deviation requires IRB review and the PI has approved it.
 *
 * Only fires for IRB_REVIEW escalations with PI APPROVED status. SPONSOR_NOTIFICATION
 * and other escalation requirements are handled by different consumers. PI REJECTED or
 * EXPIRED deviations do not proceed to IRB review.
 */
@ApplicationScoped
public class IrbDeviationCaseService {

    @Inject ClinicalDeviationCaseHub caseHub;

    public void onDeviationResolved(@ObservesAsync ProtocolDeviationResolvedEvent event) {
        if (event.escalationRequirement() != EscalationRequirement.IRB_REVIEW) {
            return;
        }
        if (event.terminalStatus() != PiApprovalStatus.APPROVED) {
            return;
        }

        Map<String, Object> initialContext = new HashMap<>();
        initialContext.put("deviationId", event.deviationId().toString());
        initialContext.put("siteId", event.siteId().toString());
        initialContext.put("severity", event.severity().name());
        initialContext.put("deviationType", event.deviationType());
        initialContext.put("piId", event.piId());
        initialContext.put("irbConsultationRequired", true);

        caseHub.startCase(initialContext);
    }
}
