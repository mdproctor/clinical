package io.casehub.clinical.api;

import io.casehub.clinical.api.model.CtcaeGrade;
import java.util.UUID;

/**
 * Carries all context needed for a safety officer adverse event notification.
 *
 * @param aeId        the adverse event being reported
 * @param enrollmentId the patient enrollment associated with the event
 * @param siteId      the trial site where the event occurred
 * @param grade       CTCAE v5.0 grade — Grade 5 (Death) warrants CRITICAL urgency
 * @param connectorId the connector to use for delivery (e.g. "slack", "teams")
 * @param destination connector-specific target (e.g. webhook URL, email address)
 */
public record SafetyOfficerNotificationRequest(
    UUID aeId,
    UUID enrollmentId,
    UUID siteId,
    CtcaeGrade grade,
    String connectorId,
    String destination
) {}
