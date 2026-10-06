package io.casehub.clinical.scenario;

import io.casehub.clinical.api.ClinicalGroups;
import io.casehub.clinical.entity.ClinicalTrial;
import io.casehub.clinical.entity.PatientEnrollment;
import io.casehub.clinical.entity.TrialSite;
import io.casehub.pages.playbook.client.ActionContext;
import io.casehub.platform.testing.FixedCurrentPrincipal;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusTest
@TestSecurity(user = "test-actor", roles = {ClinicalGroups.SPONSOR, ClinicalGroups.INVESTIGATOR, ClinicalGroups.COORDINATOR})
class PlaybookSmokeTest {

    @Inject ClinicalPlaybookActions actions;
    @Inject FixedCurrentPrincipal principal;
    @Inject
            jakarta.persistence.EntityManager em;


    @AfterEach
    void resetPrincipal() { principal.reset(); }

    private static ActionContext ctx(Map<String, Object> data) {
        return ActionContext.of("smoke-test", data, Map.of());
    }

    @Test
    @Transactional
    void full_scenario_server_steps_produce_expected_entities() {
        var trial = actions.createTrial(ctx(Map.of(
                "protocolId", "SMOKE-" + UUID.randomUUID(),
                "phase", "PHASE_III",
                "sponsor", "Smoke Test",
                "targetEnrollment", 50)));
        String trialId = trial.get("trialId").toString();

        actions.activateTrial(ctx(Map.of("trialId", trialId)));

        var siteA = actions.addSite(ctx(Map.of("trialId", trialId, "investigatorId", "dr-smoke-a")));
        var siteB = actions.addSite(ctx(Map.of("trialId", trialId, "investigatorId", "dr-smoke-b")));

        var patient = actions.enrollPatient(ctx(Map.of(
                "trialId", trialId,
                "siteId", siteA.get("siteId").toString(),
                "patientId", "PAT-SMOKE-001")));

        ClinicalTrial t = em.find(ClinicalTrial.class, UUID.fromString(trialId));
        assertNotNull(t);
        assertEquals("ACTIVE", t.status.name());

        long siteCount = em.createQuery("SELECT COUNT(s) FROM TrialSite s WHERE s.trialId = :trialId", Long.class).setParameter("trialId", UUID.fromString(trialId)).getSingleResult();
        assertEquals(2, siteCount);

        PatientEnrollment enrollment = em.find(PatientEnrollment.class,
                UUID.fromString(patient.get("enrollmentId").toString()));
        assertNotNull(enrollment);
        assertEquals("PAT-SMOKE-001", enrollment.patientId);
    }

    @Test
    void reportAdverseEvent_creates_ae_with_sla() {
        var trial = actions.createTrial(ctx(Map.of(
                "protocolId", "AE-SMOKE-" + UUID.randomUUID(),
                "phase", "PHASE_III",
                "sponsor", "S",
                "targetEnrollment", 10)));
        String trialId = trial.get("trialId").toString();
        var site = actions.addSite(ctx(Map.of("trialId", trialId, "investigatorId", "dr-ae")));
        var patient = actions.enrollPatient(ctx(Map.of(
                "trialId", trialId,
                "siteId", site.get("siteId").toString(),
                "patientId", "PAT-AE-001")));

        var ae = actions.reportAdverseEvent(ctx(Map.of(
                "trialId", trialId,
                "siteId", site.get("siteId").toString(),
                "enrollmentId", patient.get("enrollmentId").toString(),
                "grade", "GRADE_4",
                "unexpected", "true",
                "suspected", "true")));

        assertNotNull(ae.get("aeId"));
        assertNotNull(ae.get("slaDeadline"));
    }
}
