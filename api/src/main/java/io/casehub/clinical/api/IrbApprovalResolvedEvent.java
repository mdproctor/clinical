package io.casehub.clinical.api;

import io.casehub.clinical.api.model.IrbDecision;
import java.time.Instant;
import java.util.UUID;

/**
 * CDI event fired when an IRB consultation case reaches any terminal decision:
 * APPROVED, REJECTED, DEFERRED, or EXPIRED.
 *
 * <p>Follows {@link ProtocolDeviationResolvedEvent} pattern. Layer 6 consumers
 * (trial-level aggregation, DSMB rollup) observe this for cross-site signals.
 */
public record IrbApprovalResolvedEvent(
    UUID approvalId,
    UUID deviationId,
    UUID siteId,
    IrbDecision decision,
    Instant decidedAt) {}
