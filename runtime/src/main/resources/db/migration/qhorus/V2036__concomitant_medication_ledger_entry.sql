CREATE TABLE concomitant_medication_ledger_entry (
    id              UUID        NOT NULL,
    medication_id   UUID        NOT NULL,
    enrollment_id   UUID        NOT NULL,
    medication_name VARCHAR(255) NOT NULL,
    dose            VARCHAR(100) NOT NULL,
    unit            VARCHAR(50) NOT NULL,
    route           VARCHAR(50) NOT NULL,
    frequency       VARCHAR(50) NOT NULL,
    start_date      DATE        NOT NULL,
    CONSTRAINT pk_concomitant_medication_ledger_entry PRIMARY KEY (id),
    CONSTRAINT fk_concomitant_medication_ledger_entry_base FOREIGN KEY (id) REFERENCES ledger_entry(id)
);
