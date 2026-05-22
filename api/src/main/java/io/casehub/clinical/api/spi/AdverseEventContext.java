package io.casehub.clinical.api.spi;

import io.casehub.clinical.api.model.CtcaeGrade;
import java.util.UUID;

/**
 * Input context for {@link AdverseEventEscalationPolicy}. Carries all AE
 * identifiers and the CTCAE grade that determines escalation requirements.
 * Mirrors {@link DeviationContext} pattern.
 */
public record AdverseEventContext(
    UUID aeId,
    UUID enrollmentId,
    UUID siteId,
    CtcaeGrade grade) {}
