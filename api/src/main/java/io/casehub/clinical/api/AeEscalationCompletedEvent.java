package io.casehub.clinical.api;

import io.casehub.clinical.api.model.CtcaeGrade;
import java.time.Instant;
import java.util.UUID;

/**
 * CDI event fired when an AE escalation case completes — all required safety
 * reviews (senior monitor, and DSMB if Grade 4+) have been resolved.
 */
public record AeEscalationCompletedEvent(
    UUID aeId,
    CtcaeGrade grade,
    String safetyReviewOutcome,
    boolean dsmbEscalated,
    Instant completedAt) {}
