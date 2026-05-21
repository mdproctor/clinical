package io.casehub.clinical.api;

import io.casehub.clinical.api.model.DeviationSeverity;
import io.casehub.clinical.api.model.PiApprovalStatus;
import java.util.UUID;

public record SponsorNotificationRequest(
    UUID deviationId,
    UUID siteId,
    UUID trialId,
    String deviationType,
    DeviationSeverity severity,
    PiApprovalStatus terminalStatus,
    String piId,
    String sponsorNotificationConnectorId,
    String sponsorNotificationDestination
) {}
