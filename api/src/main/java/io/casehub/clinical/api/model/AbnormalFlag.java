package io.casehub.clinical.api.model;

/** Lab result abnormal flag per FHIR Observation.interpretation codes. */
public enum AbnormalFlag {
    NORMAL, LOW, HIGH, CRITICAL_LOW, CRITICAL_HIGH
}
