package io.casehub.clinical.api.model;

/** Vital sign measurement types per FHIR Observation vital-signs profile. */
public enum VitalType {
    HEART_RATE, BP_SYSTOLIC, BP_DIASTOLIC, TEMPERATURE,
    RESPIRATORY_RATE, O2_SATURATION, WEIGHT, HEIGHT
}
