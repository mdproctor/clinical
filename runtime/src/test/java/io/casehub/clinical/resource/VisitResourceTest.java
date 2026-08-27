package io.casehub.clinical.resource;

import io.casehub.clinical.api.ClinicalGroups;
import io.casehub.platform.testing.FixedCurrentPrincipal;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
@TestSecurity(user = "test-actor", roles = {ClinicalGroups.SPONSOR, ClinicalGroups.INVESTIGATOR, ClinicalGroups.COORDINATOR})
class VisitResourceTest {

    @Inject FixedCurrentPrincipal principal;

    private UUID trialId;
    private UUID siteId;
    private UUID enrollmentId;

    @AfterEach
    void resetPrincipal() { principal.reset(); }

    @BeforeEach
    void setup() {
        String trialLoc = given().contentType("application/json")
                .body("{\"protocolId\":\"VIS-" + UUID.randomUUID() + "\",\"phase\":\"PHASE_II\",\"sponsor\":\"S\",\"targetEnrollment\":10}")
                .when().post("/trials").then().statusCode(201).extract().header("Location");
        trialId = UUID.fromString(trialLoc.substring(trialLoc.lastIndexOf('/') + 1));

        String siteLoc = given().contentType("application/json")
                .body("{\"investigatorId\":\"pi-vis\"}")
                .when().post("/trials/{t}/sites", trialId).then().statusCode(201).extract().header("Location");
        siteId = UUID.fromString(siteLoc.substring(siteLoc.lastIndexOf('/') + 1));

        String enrollLoc = given().contentType("application/json")
                .body("{\"patientId\":\"PAT-VIS-001\"}")
                .when().post("/trials/{t}/sites/{s}/patients", trialId, siteId).then().statusCode(201).extract().header("Location");
        enrollmentId = UUID.fromString(enrollLoc.substring(enrollLoc.lastIndexOf('/') + 1));
    }

    private String basePath() {
        return "/trials/" + trialId + "/sites/" + siteId + "/patients/" + enrollmentId + "/visits";
    }

    @Test
    void post_visit_returns_201_and_get_retrieves() {
        String loc = given().contentType("application/json")
                .body("{\"visitType\":\"BASELINE\",\"visitDate\":\"2026-08-15T09:00:00Z\",\"status\":\"SCHEDULED\"}")
                .when().post(basePath()).then().statusCode(201).extract().header("Location");

        given().when().get(loc).then().statusCode(200)
                .body("visitType", equalTo("BASELINE"))
                .body("status", equalTo("SCHEDULED"));
    }

    @Test
    void list_visits_returns_collection() {
        given().contentType("application/json")
                .body("{\"visitType\":\"SCREENING\",\"visitDate\":\"2026-08-14T09:00:00Z\",\"status\":\"COMPLETED\"}")
                .when().post(basePath()).then().statusCode(201);
        given().contentType("application/json")
                .body("{\"visitType\":\"BASELINE\",\"visitDate\":\"2026-08-15T09:00:00Z\",\"status\":\"SCHEDULED\"}")
                .when().post(basePath()).then().statusCode(201);

        given().when().get(basePath()).then().statusCode(200).body("size()", equalTo(2));
    }

    @Test
    void patch_visit_updates_status() {
        String loc = given().contentType("application/json")
                .body("{\"visitType\":\"FOLLOW_UP\",\"visitDate\":\"2026-08-20T09:00:00Z\",\"status\":\"SCHEDULED\"}")
                .when().post(basePath()).then().statusCode(201).extract().header("Location");

        given().contentType("application/json")
                .body("{\"status\":\"COMPLETED\",\"notes\":\"All assessments done\"}")
                .when().patch(loc).then().statusCode(200)
                .body("status", equalTo("COMPLETED"))
                .body("notes", equalTo("All assessments done"));
    }

    @Test
    void post_visit_with_missing_required_field_returns_400() {
        given().contentType("application/json")
                .body("{\"visitType\":\"BASELINE\"}")
                .when().post(basePath()).then().statusCode(400);
    }
}
