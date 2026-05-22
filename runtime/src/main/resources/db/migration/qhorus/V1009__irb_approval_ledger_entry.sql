-- V1009: IRB approval ledger entries — tamper-evident FDA audit record for IRB decisions.
-- JOINED inheritance from ledger_entry. Mirrors V1005 (ae_ledger_entry) pattern.
CREATE TABLE irb_approval_ledger_entry (
    id               UUID         NOT NULL,
    irb_approval_id  UUID         NOT NULL,
    deviation_id     UUID         NOT NULL,
    irb_decision     VARCHAR(50)  NOT NULL,
    committee_id     VARCHAR(255) NOT NULL,
    decided_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_irb_approval_ledger_entry PRIMARY KEY (id),
    CONSTRAINT fk_irb_approval_le_ledger FOREIGN KEY (id) REFERENCES ledger_entry(id)
);
