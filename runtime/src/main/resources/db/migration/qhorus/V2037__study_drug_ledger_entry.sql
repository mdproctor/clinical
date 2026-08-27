CREATE TABLE study_drug_ledger_entry (
    id              UUID        NOT NULL,
    drug_admin_id   UUID        NOT NULL,
    enrollment_id   UUID        NOT NULL,
    drug_name       VARCHAR(255) NOT NULL,
    dose            VARCHAR(100) NOT NULL,
    unit            VARCHAR(50) NOT NULL,
    route           VARCHAR(50) NOT NULL,
    administered_by VARCHAR(255) NOT NULL,
    drug_status     VARCHAR(50) NOT NULL,
    CONSTRAINT pk_study_drug_ledger_entry PRIMARY KEY (id),
    CONSTRAINT fk_study_drug_ledger_entry_base FOREIGN KEY (id) REFERENCES ledger_entry(id)
);
