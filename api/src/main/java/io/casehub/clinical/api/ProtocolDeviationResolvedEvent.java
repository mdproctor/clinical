package io.casehub.clinical.api;

import io.casehub.clinical.api.model.DeviationSeverity;
import io.casehub.clinical.api.model.EscalationRequirement;
import io.casehub.clinical.api.model.PiApprovalStatus;
import java.util.UUID;

/**
 * Fired when a protocol deviation reaches a terminal PI authorisation state.
 * Consumers: casehubio/clinical#6 (IRB_REVIEW) and casehubio/clinical#13 (SPONSOR_NOTIFICATION).
 *
 * <p>{@code piId} is null when {@code terminalStatus} is EXPIRED (system-initiated).
 * Consumers that require a named PI must check before use.
 */
public record ProtocolDeviationResolvedEvent(
    UUID deviationId,
    UUID siteId,
    DeviationSeverity severity,
    EscalationRequirement escalationRequirement,
    PiApprovalStatus terminalStatus,
    String deviationType,
    String piId
) {}
