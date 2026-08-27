CREATE TABLE lab_result (
    id                   UUID            NOT NULL,
    tenant_id            VARCHAR(255)    NOT NULL,
    enrollment_id        UUID            NOT NULL,
    visit_id             UUID,
    test_name            VARCHAR(255)    NOT NULL,
    result_value         DECIMAL(19,4)   NOT NULL,
    unit                 VARCHAR(50)     NOT NULL,
    reference_range_low  DECIMAL(19,4),
    reference_range_high DECIMAL(19,4),
    abnormal_flag        VARCHAR(50)     NOT NULL,
    specimen_type        VARCHAR(50)     NOT NULL,
    performing_lab       VARCHAR(255),
    collected_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at           TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_lab_result PRIMARY KEY (id)
);
