package io.casehub.clinical.api;

import io.casehub.clinical.api.model.DeviationSeverity;
import io.casehub.clinical.api.model.PiApprovalStatus;
import java.util.UUID;

/**
 * All context the SponsorNotifier SPI needs to deliver and record a sponsor notification.
 *
 * <p>{@code piId} and {@code piDisplayName} are null when {@code terminalStatus} is EXPIRED
 * (system-initiated expiration — no PI actor). Consumers must check before using either field.
 *
 * <p>{@code piDisplayName} is the formal name resolved by {@code PiIdentityResolver} for use
 * in regulated notification bodies. Falls back to {@code piId} when no resolver override is
 * configured (default passthrough).
 */
public record SponsorNotificationRequest(
    UUID trialId,
    UUID siteId,
    UUID deviationId,
    String deviationType,
    DeviationSeverity severity,
    PiApprovalStatus terminalStatus,
    String piId,
    String piDisplayName,
    String sponsorNotificationConnectorId,
    String sponsorNotificationDestination,
    String tenantId
) {}
