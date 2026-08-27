CREATE TABLE lab_result_ledger_entry (
    id              UUID            NOT NULL,
    lab_result_id   UUID            NOT NULL,
    enrollment_id   UUID            NOT NULL,
    test_name       VARCHAR(255)    NOT NULL,
    result_value    DECIMAL(19,4)   NOT NULL,
    unit            VARCHAR(50)     NOT NULL,
    abnormal_flag   VARCHAR(50)     NOT NULL,
    specimen_type   VARCHAR(50)     NOT NULL,
    CONSTRAINT pk_lab_result_ledger_entry PRIMARY KEY (id),
    CONSTRAINT fk_lab_result_ledger_entry_base FOREIGN KEY (id) REFERENCES ledger_entry(id)
);
