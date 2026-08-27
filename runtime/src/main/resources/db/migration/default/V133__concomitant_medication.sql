CREATE TABLE concomitant_medication (
    id              UUID            NOT NULL,
    tenant_id       VARCHAR(255)    NOT NULL,
    enrollment_id   UUID            NOT NULL,
    medication_name VARCHAR(255)    NOT NULL,
    indication      VARCHAR(500),
    dose            VARCHAR(100)    NOT NULL,
    unit            VARCHAR(50)     NOT NULL,
    route           VARCHAR(50)     NOT NULL,
    frequency       VARCHAR(50)     NOT NULL,
    start_date      DATE            NOT NULL,
    end_date        DATE,
    ongoing         BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_concomitant_medication PRIMARY KEY (id)
);
