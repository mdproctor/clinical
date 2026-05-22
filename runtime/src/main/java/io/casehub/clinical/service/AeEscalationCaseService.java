package io.casehub.clinical.service;

import io.casehub.clinical.api.AdverseEventReportedEvent;
import io.casehub.clinical.api.spi.AdverseEventContext;
import io.casehub.clinical.api.spi.AdverseEventEscalationPolicy;
import io.casehub.clinical.api.spi.AdverseEventEscalationRequirements;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;
import java.util.HashMap;
import java.util.Map;

/**
 * Observes AdverseEventReportedEvent (Grade 3+ AEs) and starts an AE escalation
 * engine case. Re-evaluates AdverseEventEscalationPolicy to populate initial context.
 *
 * <p>Policy is called twice — once in AdverseEventService (routing decision),
 * once here (case context keys). This is intentional: each consumer calls the
 * policy for its own concern. The SPI must be idempotent.
 */
@ApplicationScoped
public class AeEscalationCaseService {

    @Inject ClinicalAdverseEventCaseHub caseHub;
    @Inject AdverseEventEscalationPolicy policy;

    public void onAdverseEventReported(@ObservesAsync AdverseEventReportedEvent event) {
        AdverseEventEscalationRequirements requirements = policy.evaluate(
                new AdverseEventContext(event.aeId(), event.enrollmentId(), event.siteId(), event.grade()));

        Map<String, Object> initialContext = new HashMap<>();
        initialContext.put("aeId", event.aeId().toString());
        initialContext.put("enrollmentId", event.enrollmentId().toString());
        initialContext.put("grade", event.grade().name());
        initialContext.put("requiresSeniorMonitor", requirements.requiresSeniorMonitor());
        initialContext.put("requiresDsmbEscalation", requirements.requiresDsmbEscalation());

        caseHub.startCase(initialContext);
    }
}
