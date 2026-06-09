-- V116: Multi-tenancy foundation — tenant_id column on all six domain entities.
-- DEFAULT 'default' ensures safe migration on existing rows.
-- Query isolation (filtering reads by tenant_id) is tracked in casehubio/clinical#71.
-- Note: sponsor_notification.tenant_id already exists from V115 (nullable VARCHAR(64)).
ALTER TABLE clinical_trial     ADD COLUMN tenant_id VARCHAR(255) NOT NULL DEFAULT 'default';
ALTER TABLE trial_site         ADD COLUMN tenant_id VARCHAR(255) NOT NULL DEFAULT 'default';
ALTER TABLE patient_enrollment ADD COLUMN tenant_id VARCHAR(255) NOT NULL DEFAULT 'default';
ALTER TABLE protocol_deviation ADD COLUMN tenant_id VARCHAR(255) NOT NULL DEFAULT 'default';
ALTER TABLE adverse_event      ADD COLUMN tenant_id VARCHAR(255) NOT NULL DEFAULT 'default';
ALTER TABLE irb_approval       ADD COLUMN tenant_id VARCHAR(255) NOT NULL DEFAULT 'default';
