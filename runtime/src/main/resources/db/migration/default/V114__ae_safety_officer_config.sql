-- V114: safety officer notification config on ClinicalTrial.
-- Nullable — missing config causes SafetyOfficerNotificationListener to skip with a warn log.
-- VARCHAR(2048) for destination — consistent with V108 (sponsor_notification_destination).
ALTER TABLE clinical_trial ADD COLUMN safety_officer_connector_id VARCHAR(255);
ALTER TABLE clinical_trial ADD COLUMN safety_officer_destination  VARCHAR(2048);
