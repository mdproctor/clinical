-- V1011: tamper-evident safety officer AE notification delivery records.
-- JOINED inheritance from ledger_entry. Mirrors V1010 (ae_escalation_ledger_entry) pattern.
-- ICH E6(R3) §5.17 / 21 CFR 312.32: delivery success/failure must be independently verifiable.
CREATE TABLE safety_officer_notification_ledger_entry (
    id            UUID         NOT NULL,
    ae_id         UUID         NOT NULL,
    enrollment_id UUID         NOT NULL,
    site_id       UUID         NOT NULL,
    ctcae_grade   VARCHAR(50)  NOT NULL,
    connector_id  VARCHAR(255),
    destination   VARCHAR(2048),
    delivered     BOOLEAN      NOT NULL DEFAULT FALSE,
    notified_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_so_notification_ledger_entry PRIMARY KEY (id),
    CONSTRAINT fk_so_notification_le_ledger FOREIGN KEY (id) REFERENCES ledger_entry(id)
);
