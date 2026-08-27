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
class VitalSignResourceTest {

    @Inject FixedCurrentPrincipal principal;
    private UUID trialId, siteId, enrollmentId;

    @AfterEach void resetPrincipal() { principal.reset(); }

    @BeforeEach
    void setup() {
        String trialLoc = given().contentType("application/json")
                .body("{\"protocolId\":\"VIT-" + UUID.randomUUID() + "\",\"phase\":\"PHASE_II\",\"sponsor\":\"S\",\"targetEnrollment\":10}")
                .when().post("/trials").then().statusCode(201).extract().header("Location");
        trialId = UUID.fromString(trialLoc.substring(trialLoc.lastIndexOf('/') + 1));
        String siteLoc = given().contentType("application/json").body("{\"investigatorId\":\"pi-vit\"}")
                .when().post("/trials/{t}/sites", trialId).then().statusCode(201).extract().header("Location");
        siteId = UUID.fromString(siteLoc.substring(siteLoc.lastIndexOf('/') + 1));
        String enrollLoc = given().contentType("application/json").body("{\"patientId\":\"PAT-VIT-001\"}")
                .when().post("/trials/{t}/sites/{s}/patients", trialId, siteId).then().statusCode(201).extract().header("Location");
        enrollmentId = UUID.fromString(enrollLoc.substring(enrollLoc.lastIndexOf('/') + 1));
    }

    private String basePath() {
        return "/trials/" + trialId + "/sites/" + siteId + "/patients/" + enrollmentId + "/vitals";
    }

    @Test
    void post_vital_returns_201_and_get_retrieves() {
        String loc = given().contentType("application/json")
                .body("{\"type\":\"BP_SYSTOLIC\",\"value\":120,\"unit\":\"mmHg\",\"measuredAt\":\"2026-08-15T09:00:00Z\"}")
                .when().post(basePath()).then().statusCode(201).extract().header("Location");
        given().when().get(loc).then().statusCode(200)
                .body("type", equalTo("BP_SYSTOLIC"))
                .body("unit", equalTo("mmHg"));
    }

    @Test
    void list_vitals_returns_collection() {
        given().contentType("application/json")
                .body("{\"type\":\"HEART_RATE\",\"value\":72,\"unit\":\"bpm\",\"measuredAt\":\"2026-08-15T09:00:00Z\"}")
                .when().post(basePath()).then().statusCode(201);
        given().contentType("application/json")
                .body("{\"type\":\"TEMPERATURE\",\"value\":36.8,\"unit\":\"C\",\"measuredAt\":\"2026-08-15T09:05:00Z\"}")
                .when().post(basePath()).then().statusCode(201);
        given().when().get(basePath()).then().statusCode(200).body("size()", equalTo(2));
    }
}
