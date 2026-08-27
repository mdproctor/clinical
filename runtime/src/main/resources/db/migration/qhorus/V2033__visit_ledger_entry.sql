CREATE TABLE visit_ledger_entry (
    id              UUID        NOT NULL,
    visit_id        UUID        NOT NULL,
    enrollment_id   UUID        NOT NULL,
    visit_type      VARCHAR(50) NOT NULL,
    visit_date      TIMESTAMP WITH TIME ZONE NOT NULL,
    visit_status    VARCHAR(50) NOT NULL,
    CONSTRAINT pk_visit_ledger_entry PRIMARY KEY (id),
    CONSTRAINT fk_visit_ledger_entry_base FOREIGN KEY (id) REFERENCES ledger_entry(id)
);
