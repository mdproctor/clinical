package io.casehub.clinical.api;

/**
 * SPI for delivering adverse event notifications to the safety officer.
 *
 * <p>Deployers override the {@code @DefaultBean} implementation to customise delivery channel,
 * routing logic, or integration. Grade information is available in the request — a custom
 * implementation can route Grade 5 (Death) to an emergency pager and lower grades to Slack.
 *
 * <p>GCP ICH E6(R3) §5.17 / 21 CFR 312.32: notification delivery (success or failure)
 * must be recorded in the tamper-evident audit trail. Implementations that replace the
 * default must also write a ledger entry.
 */
public interface SafetyOfficerNotifier {

    void notify(SafetyOfficerNotificationRequest request);
}
