package io.casehub.clinical.resource;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

@QuarkusTest
class AdverseEventResourceTest {

    // ── Setup helpers ─────────────────────────────────────────────────────────

    private UUID createTrial() {
        String loc = given()
            .contentType("application/json")
            .body("""
                {"protocolId":"AE-TEST-%s","phase":"PHASE_II","sponsor":"Test","targetEnrollment":10}
                """.formatted(UUID.randomUUID()))
            .when().post("/trials")
            .then().statusCode(201).extract().header("Location");
        return UUID.fromString(loc.substring(loc.lastIndexOf('/') + 1));
    }

    private UUID addSite(UUID trialId) {
        String loc = given()
            .contentType("application/json")
            .body("{\"investigatorId\":\"pi-ae-test\"}")
            .when().post("/trials/{t}/sites", trialId)
            .then().statusCode(201).extract().header("Location");
        return UUID.fromString(loc.substring(loc.lastIndexOf('/') + 1));
    }

    private UUID enrollPatient(UUID trialId, UUID siteId) {
        String loc = given()
            .contentType("application/json")
            .body("{\"patientId\":\"PAT-" + UUID.randomUUID() + "\"}")
            .when().post("/trials/{t}/sites/{s}/patients", trialId, siteId)
            .then().statusCode(201).extract().header("Location");
        return UUID.fromString(loc.substring(loc.lastIndexOf('/') + 1));
    }

    // ── Happy path ────────────────────────────────────────────────────────────

    @Test
    void grade3_ae_returns_201_engine_managed_no_direct_workItemId() {
        // Grade 3+ AEs are engine-managed: the service fires AdverseEventReportedEvent and
        // does NOT create a WorkItem directly. workItemId is null on the AE record — the engine
        // creates WorkItems via humanTask bindings in ae-escalation.yaml.
        UUID trialId = createTrial();
        UUID siteId = addSite(trialId);
        UUID enrollmentId = enrollPatient(trialId, siteId);

        given()
            .contentType("application/json")
            .body("""
                {"grade":"GRADE_3","occurredAt":"%s"}
                """.formatted(Instant.now().minusSeconds(3600)))
        .when()
            .post("/trials/{t}/sites/{s}/patients/{e}/adverse-events",
                  trialId, siteId, enrollmentId)
        .then()
            .statusCode(201)
            .body("workItemId", nullValue())
            .body("slaDeadline", notNullValue())
            .body("grade", equalTo("GRADE_3"))
            .header("Location", containsString("/adverse-events/"));
    }

    @Test
    void grade1_ae_returns_201_with_direct_workItemId() {
        // Grade 1/2 AEs use direct WorkItem creation (Layer 2 path preserved).
        UUID trialId = createTrial();
        UUID siteId = addSite(trialId);
        UUID enrollmentId = enrollPatient(trialId, siteId);

        given()
            .contentType("application/json")
            .body("""
                {"grade":"GRADE_1","occurredAt":"%s"}
                """.formatted(Instant.now().minusSeconds(3600)))
        .when()
            .post("/trials/{t}/sites/{s}/patients/{e}/adverse-events",
                  trialId, siteId, enrollmentId)
        .then()
            .statusCode(201)
            .body("workItemId", notNullValue())
            .body("slaDeadline", notNullValue())
            .body("grade", equalTo("GRADE_1"));
    }

    @Test
    void grade5_ae_slaDeadline_is_approximately_1h_from_now() {
        UUID trialId = createTrial();
        UUID siteId = addSite(trialId);
        UUID enrollmentId = enrollPatient(trialId, siteId);

        String slaDeadline = given()
            .contentType("application/json")
            .body("""
                {"grade":"GRADE_5","occurredAt":"%s"}
                """.formatted(Instant.now().minusSeconds(60)))
        .when()
            .post("/trials/{t}/sites/{s}/patients/{e}/adverse-events",
                  trialId, siteId, enrollmentId)
        .then()
            .statusCode(201)
            .extract().path("slaDeadline");

        Instant deadline = Instant.parse(slaDeadline);
        long secondsUntilDeadline = deadline.getEpochSecond() - Instant.now().getEpochSecond();
        // slaDeadline = reportedAt + 1h; reportedAt ≈ now; gap ≈ 3600s (allow 30s tolerance)
        assertApproxSeconds(secondsUntilDeadline, 3600, 30);
    }

    // ── Robustness ────────────────────────────────────────────────────────────

    @Test
    void non_existent_enrollment_returns_404() {
        UUID trialId = createTrial();
        UUID siteId = addSite(trialId);
        UUID fakeEnrollment = UUID.randomUUID();

        given()
            .contentType("application/json")
            .body("""
                {"grade":"GRADE_3","occurredAt":"%s"}
                """.formatted(Instant.now()))
        .when()
            .post("/trials/{t}/sites/{s}/patients/{e}/adverse-events",
                  trialId, siteId, fakeEnrollment)
        .then()
            .statusCode(404);
    }

    @Test
    void missing_grade_returns_400() {
        UUID trialId = createTrial();
        UUID siteId = addSite(trialId);
        UUID enrollmentId = enrollPatient(trialId, siteId);

        given()
            .contentType("application/json")
            .body("""
                {"occurredAt":"%s"}
                """.formatted(Instant.now()))
        .when()
            .post("/trials/{t}/sites/{s}/patients/{e}/adverse-events",
                  trialId, siteId, enrollmentId)
        .then()
            .statusCode(400);
    }

    @Test
    void missing_occurredAt_returns_400() {
        UUID trialId = createTrial();
        UUID siteId = addSite(trialId);
        UUID enrollmentId = enrollPatient(trialId, siteId);

        given()
            .contentType("application/json")
            .body("{\"grade\":\"GRADE_3\"}")
        .when()
            .post("/trials/{t}/sites/{s}/patients/{e}/adverse-events",
                  trialId, siteId, enrollmentId)
        .then()
            .statusCode(400);
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private void assertApproxSeconds(long actual, long expected, long toleranceSeconds) {
        if (Math.abs(actual - expected) > toleranceSeconds) {
            throw new AssertionError(
                "SLA gap is " + actual + "s, expected ~" + expected + "s (±" + toleranceSeconds + "s)");
        }
    }
}
