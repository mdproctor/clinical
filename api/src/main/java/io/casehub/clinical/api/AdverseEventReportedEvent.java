package io.casehub.clinical.api;

import io.casehub.clinical.api.model.CtcaeGrade;
import java.time.Instant;
import java.util.UUID;

/**
 * CDI event fired when a Grade 3+ adverse event is reported and requires
 * engine-managed escalation. Grade 1/2 AEs use direct WorkItem creation
 * and do not fire this event.
 *
 * <p>Consumer: {@code AeEscalationCaseService} — starts the AE escalation
 * engine case and creates humanTask WorkItems via the YAML bindings.
 */
public record AdverseEventReportedEvent(
    UUID aeId,
    UUID enrollmentId,
    UUID siteId,
    CtcaeGrade grade,
    Instant reportedAt,
    String tenantId) {}
