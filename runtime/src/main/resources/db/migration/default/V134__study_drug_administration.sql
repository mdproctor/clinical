CREATE TABLE study_drug_administration (
    id              UUID            NOT NULL,
    tenant_id       VARCHAR(255)    NOT NULL,
    enrollment_id   UUID            NOT NULL,
    visit_id        UUID,
    drug_name       VARCHAR(255)    NOT NULL,
    dose            VARCHAR(100)    NOT NULL,
    unit            VARCHAR(50)     NOT NULL,
    route           VARCHAR(50)     NOT NULL,
    administered_at TIMESTAMP WITH TIME ZONE NOT NULL,
    administered_by VARCHAR(255)    NOT NULL,
    batch_number    VARCHAR(255),
    status          VARCHAR(50)     NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_study_drug_administration PRIMARY KEY (id)
);
