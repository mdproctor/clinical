package io.casehub.clinical.api;

import io.casehub.clinical.api.model.DeviationSeverity;
import io.casehub.clinical.api.model.PiApprovalStatus;
import java.util.UUID;

/**
 * All context the SponsorNotifier SPI needs to deliver and record a sponsor notification.
 *
 * <p>{@code piId} is null when {@code terminalStatus} is EXPIRED (system-initiated expiration).
 * Consumers must check before using piId in message content.
 */
public record SponsorNotificationRequest(
    UUID trialId,
    UUID siteId,
    UUID deviationId,
    String deviationType,
    DeviationSeverity severity,
    PiApprovalStatus terminalStatus,
    String piId,
    String sponsorNotificationConnectorId,
    String sponsorNotificationDestination
) {}
