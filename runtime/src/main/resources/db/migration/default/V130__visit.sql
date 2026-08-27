CREATE TABLE visit (
    id              UUID            NOT NULL,
    tenant_id       VARCHAR(255)    NOT NULL,
    enrollment_id   UUID            NOT NULL,
    visit_type      VARCHAR(50)     NOT NULL,
    visit_date      TIMESTAMP WITH TIME ZONE NOT NULL,
    status          VARCHAR(50)     NOT NULL,
    notes           VARCHAR(2000),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_visit PRIMARY KEY (id)
);
