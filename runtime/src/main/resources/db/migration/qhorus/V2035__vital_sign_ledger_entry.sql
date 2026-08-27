CREATE TABLE vital_sign_ledger_entry (
    id              UUID            NOT NULL,
    vital_sign_id   UUID            NOT NULL,
    enrollment_id   UUID            NOT NULL,
    vital_type      VARCHAR(50)     NOT NULL,
    result_value    DECIMAL(19,4)   NOT NULL,
    unit            VARCHAR(50)     NOT NULL,
    CONSTRAINT pk_vital_sign_ledger_entry PRIMARY KEY (id),
    CONSTRAINT fk_vital_sign_ledger_entry_base FOREIGN KEY (id) REFERENCES ledger_entry(id)
);
