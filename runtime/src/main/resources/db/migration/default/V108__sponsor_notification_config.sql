ALTER TABLE clinical_trial
    ADD COLUMN sponsor_notification_connector_id VARCHAR(64),
    ADD COLUMN sponsor_notification_destination  VARCHAR(512);
