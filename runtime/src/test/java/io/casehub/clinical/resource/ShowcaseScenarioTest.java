package io.casehub.clinical.resource;

import io.casehub.clinical.service.PiResponseListener;
import io.casehub.qhorus.api.message.MessageType;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * End-to-end happy-path test for the 3-site oncology showcase scenario.
 * Verifies the domain layer can support the full trial registration flow
 * that Epic 3 will wire to sub-case orchestration.
 */
@QuarkusTest
class ShowcaseScenarioTest {

    @Inject PiResponseListener piResponseListener;

    @Test
    void three_site_oncology_trial_registers_correctly() {
        // Register the trial — UUID suffix avoids H2 uniqueness collision across test runs
        String trialLoc = given()
            .contentType("application/json")
            .body("""
                {
                  "protocolId": "ONCOL-PHASE3-2026-001-%s",
                  "phase": "PHASE_III",
                  "sponsor": "Acme Oncology",
                  "targetEnrollment": 300
                }
                """.formatted(UUID.randomUUID()))
        .when().post("/trials").then().statusCode(201).extract().header("Location");

        UUID trialId = UUID.fromString(trialLoc.substring(trialLoc.lastIndexOf('/') + 1));

        // Add 3 sites
        UUID siteAId = addSite(trialId, "pi-site-a-001");
        UUID siteBId = addSite(trialId, "pi-site-b-002");
        UUID siteCId = addSite(trialId, "pi-site-c-003");

        // Enroll a patient at each site
        UUID patientA = enrollPatient(trialId, siteAId, "PATIENT-SITE-A-001");
        UUID patientB = enrollPatient(trialId, siteBId, "PATIENT-SITE-B-001");
        UUID patientC = enrollPatient(trialId, siteCId, "PATIENT-SITE-C-001");

        // Verify trial
        given().when().get("/trials/{id}", trialId)
        .then()
            .statusCode(200)
            .body("phase", equalTo("PHASE_III"))
            .body("targetEnrollment", equalTo(300))
            .body("status", equalTo("PLANNING"));

        // Verify all 3 sites are retrievable under the trial
        assertSiteExists(trialId, siteAId, "pi-site-a-001");
        assertSiteExists(trialId, siteBId, "pi-site-b-002");
        assertSiteExists(trialId, siteCId, "pi-site-c-003");

        // Verify all 3 patients enrolled as CANDIDATE/PENDING
        assertEnrollmentExists(trialId, siteAId, patientA, "PATIENT-SITE-A-001");
        assertEnrollmentExists(trialId, siteBId, patientB, "PATIENT-SITE-B-001");
        assertEnrollmentExists(trialId, siteCId, patientC, "PATIENT-SITE-C-001");
    }

    private UUID addSite(UUID trialId, String investigatorId) {
        String loc = given()
            .contentType("application/json")
            .body("{\"investigatorId\": \"" + investigatorId + "\"}")
        .when()
            .post("/trials/{id}/sites", trialId)
        .then()
            .statusCode(201).extract().header("Location");
        return UUID.fromString(loc.substring(loc.lastIndexOf('/') + 1));
    }

    private UUID enrollPatient(UUID trialId, UUID siteId, String patientId) {
        String loc = given()
            .contentType("application/json")
            .body("{\"patientId\": \"" + patientId + "\"}")
        .when()
            .post("/trials/{trialId}/sites/{siteId}/patients", trialId, siteId)
        .then()
            .statusCode(201).extract().header("Location");
        return UUID.fromString(loc.substring(loc.lastIndexOf('/') + 1));
    }

    private void assertSiteExists(UUID trialId, UUID siteId, String investigatorId) {
        given().when().get("/trials/{trialId}/sites/{siteId}", trialId, siteId)
        .then()
            .statusCode(200)
            .body("investigatorId", equalTo(investigatorId))
            .body("status", equalTo("PENDING"));
    }

    private void assertEnrollmentExists(UUID trialId, UUID siteId, UUID enrollmentId, String patientId) {
        given().when().get("/trials/{t}/sites/{s}/patients/{e}", trialId, siteId, enrollmentId)
        .then()
            .statusCode(200)
            .body("patientId", equalTo(patientId))
            .body("enrollmentStatus", equalTo("CANDIDATE"))
            .body("consentStatus", equalTo("PENDING"));
    }

    private String extractId(String location) {
        return location.substring(location.lastIndexOf('/') + 1);
    }

    @Test
    void site_a_grade3_ae_gets_24h_sla_and_workItemId() {
        String trialLoc = given()
            .contentType("application/json")
            .body("""
                {
                  "protocolId": "SHOWCASE-SAE-2026-%s",
                  "phase": "PHASE_III",
                  "sponsor": "Acme Oncology",
                  "targetEnrollment": 300
                }
                """.formatted(UUID.randomUUID()))
            .when().post("/trials").then().statusCode(201).extract().header("Location");
        UUID trialId = UUID.fromString(trialLoc.substring(trialLoc.lastIndexOf('/') + 1));

        UUID siteAId = addSite(trialId, "pi-showcase-sae-001");
        UUID patientA = enrollPatient(trialId, siteAId, "PATIENT-SAE-A-001-" + UUID.randomUUID());

        String slaDeadlineStr = given()
            .contentType("application/json")
            .body("""
                {"grade":"GRADE_3","occurredAt":"%s"}
                """.formatted(Instant.now().minus(Duration.ofHours(2))))
            .when()
                .post("/trials/{t}/sites/{s}/patients/{e}/adverse-events",
                      trialId, siteAId, patientA)
            .then()
                .statusCode(201)
                // Grade 3 is engine-managed: workItemId is null; engine creates WorkItems via ae-escalation.yaml
                .body("workItemId", nullValue())
                .body("grade", equalTo("GRADE_3"))
                .extract().path("slaDeadline");

        Instant deadline = Instant.parse(slaDeadlineStr);
        Duration gap = Duration.between(Instant.now(), deadline);
        assertThat(gap.toHours()).isBetween(23L, 24L);
    }

    @Test
    void site_b_grade5_ae_gets_1h_urgent_sla() {
        String trialLoc = given()
            .contentType("application/json")
            .body("""
                {
                  "protocolId": "SHOWCASE-DEATH-2026-%s",
                  "phase": "PHASE_III",
                  "sponsor": "Acme Oncology",
                  "targetEnrollment": 300
                }
                """.formatted(UUID.randomUUID()))
            .when().post("/trials").then().statusCode(201).extract().header("Location");
        UUID trialId = UUID.fromString(trialLoc.substring(trialLoc.lastIndexOf('/') + 1));

        UUID siteBId = addSite(trialId, "pi-showcase-g5-001");
        UUID patientB = enrollPatient(trialId, siteBId, "PATIENT-G5-B-001-" + UUID.randomUUID());

        String slaDeadlineStr = given()
            .contentType("application/json")
            .body("""
                {"grade":"GRADE_5","occurredAt":"%s"}
                """.formatted(Instant.now().minus(Duration.ofMinutes(30))))
            .when()
                .post("/trials/{t}/sites/{s}/patients/{e}/adverse-events",
                      trialId, siteBId, patientB)
            .then()
                .statusCode(201)
                // Grade 5 is engine-managed: workItemId is null; engine creates WorkItems via ae-escalation.yaml
                .body("workItemId", nullValue())
                .body("grade", equalTo("GRADE_5"))
                .extract().path("slaDeadline");

        Instant deadline = Instant.parse(slaDeadlineStr);
        Duration gap = Duration.between(Instant.now(), deadline);
        assertThat(gap.toMinutes()).isBetween(55L, 65L);
    }

    @Test
    void site_c_pi_authorisation_minor_approved_critical_escalated() {
        // Register a fresh trial for this scenario
        String trialLoc = given()
            .contentType("application/json")
            .body("""
                {
                  "protocolId": "SHOWCASE-PI-AUTH-2026-%s",
                  "phase": "PHASE_III",
                  "sponsor": "Acme Oncology",
                  "targetEnrollment": 300
                }
                """.formatted(UUID.randomUUID()))
            .when().post("/trials").then().statusCode(201).extract().header("Location");
        UUID trialId = UUID.fromString(extractId(trialLoc));
        UUID siteCId = addSite(trialId, "pi-site-c-003");

        // POST MINOR deviation — expect COMMANDED with NONE escalation
        String minorLoc = given()
            .contentType("application/json")
            .body("{\"deviationType\":\"sample-window-minor\",\"severity\":\"MINOR\"}")
            .when().post("/trials/{t}/sites/{s}/deviations", trialId, siteCId)
            .then()
                .statusCode(201)
                .body("piApprovalStatus", equalTo("COMMANDED"))
                .body("escalationRequirement", equalTo("NONE"))
            .extract().header("Location");
        String minorId = extractId(minorLoc);

        // POST CRITICAL deviation — expect COMMANDED with IRB_REVIEW escalation
        String criticalLoc = given()
            .contentType("application/json")
            .body("{\"deviationType\":\"protocol-endpoint-critical\",\"severity\":\"CRITICAL\"}")
            .when().post("/trials/{t}/sites/{s}/deviations", trialId, siteCId)
            .then()
                .statusCode(201)
                .body("piApprovalStatus", equalTo("COMMANDED"))
                .body("escalationRequirement", equalTo("IRB_REVIEW"))
            .extract().header("Location");
        String criticalId = extractId(criticalLoc);

        // PI responds DONE to the minor deviation
        piResponseListener.process(
            "clinical/deviation/" + minorId + "/pi-oversight",
            MessageType.DONE,
            "human:site-c-pi"
        );

        // Minor deviation should now be APPROVED (no escalation requirement)
        given().when()
            .get("/trials/{t}/sites/{s}/deviations/{d}", trialId, siteCId, minorId)
            .then()
                .statusCode(200)
                .body("piApprovalStatus", equalTo("APPROVED"));

        // PI responds DONE to the critical deviation
        piResponseListener.process(
            "clinical/deviation/" + criticalId + "/pi-oversight",
            MessageType.DONE,
            "human:site-c-pi"
        );

        // Critical deviation should now be ESCALATED (forwarded to IRB)
        given().when()
            .get("/trials/{t}/sites/{s}/deviations/{d}", trialId, siteCId, criticalId)
            .then()
                .statusCode(200)
                .body("piApprovalStatus", equalTo("ESCALATED"));
    }
}
