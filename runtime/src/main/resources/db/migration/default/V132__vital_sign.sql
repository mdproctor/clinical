CREATE TABLE vital_sign (
    id              UUID            NOT NULL,
    tenant_id       VARCHAR(255)    NOT NULL,
    enrollment_id   UUID            NOT NULL,
    visit_id        UUID,
    vital_type      VARCHAR(50)     NOT NULL,
    result_value    DECIMAL(19,4)   NOT NULL,
    unit            VARCHAR(50)     NOT NULL,
    measured_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_vital_sign PRIMARY KEY (id)
);
