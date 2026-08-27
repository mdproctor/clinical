package io.casehub.clinical.api.model;

/** Study drug administration status per FHIR MedicationAdministration.status codes. */
public enum DrugAdminStatus {
    ADMINISTERED, HELD, DISCONTINUED, DOSE_MODIFIED
}
