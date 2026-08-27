package io.casehub.clinical.api.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClinicalEnumValuesTest {

    @Test void visitType_has_5_values() { assertEquals(5, VisitType.values().length); }

    @Test void visitStatus_has_4_values() { assertEquals(4, VisitStatus.values().length); }

    @Test void abnormalFlag_has_5_values() { assertEquals(5, AbnormalFlag.values().length); }

    @Test void specimenType_has_4_values() { assertEquals(4, SpecimenType.values().length); }

    @Test void vitalType_has_8_values() { assertEquals(8, VitalType.values().length); }

    @Test void medicationRoute_has_6_values() { assertEquals(6, MedicationRoute.values().length); }

    @Test void medicationFrequency_has_6_values() { assertEquals(6, MedicationFrequency.values().length); }

    @Test void drugAdminStatus_has_4_values() { assertEquals(4, DrugAdminStatus.values().length); }
}
