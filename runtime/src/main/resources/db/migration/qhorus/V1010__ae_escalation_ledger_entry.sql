-- V1010: AE escalation ledger entries — records safety review completion outcomes.
-- JOINED inheritance from ledger_entry. Mirrors V1005 pattern.
CREATE TABLE ae_escalation_ledger_entry (
    id                     UUID         NOT NULL,
    ae_id                  UUID         NOT NULL,
    enrollment_id          UUID         NOT NULL,
    ctcae_grade            VARCHAR(50)  NOT NULL,
    safety_review_outcome  VARCHAR(255),
    dsmb_escalated         BOOLEAN      NOT NULL DEFAULT FALSE,
    completed_at           TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_ae_escalation_ledger_entry PRIMARY KEY (id),
    CONSTRAINT fk_ae_escalation_le_ledger FOREIGN KEY (id) REFERENCES ledger_entry(id)
);
