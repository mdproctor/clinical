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
class LabResultResourceTest {

    @Inject FixedCurrentPrincipal principal;

    private UUID trialId;
    private UUID siteId;
    private UUID enrollmentId;

    @AfterEach
    void resetPrincipal() { principal.reset(); }

    @BeforeEach
    void setup() {
        String trialLoc = given().contentType("application/json")
                .body("{\"protocolId\":\"LAB-" + UUID.randomUUID() + "\",\"phase\":\"PHASE_II\",\"sponsor\":\"S\",\"targetEnrollment\":10}")
                .when().post("/trials").then().statusCode(201).extract().header("Location");
        trialId = UUID.fromString(trialLoc.substring(trialLoc.lastIndexOf('/') + 1));

        String siteLoc = given().contentType("application/json")
                .body("{\"investigatorId\":\"pi-lab\"}")
                .when().post("/trials/{t}/sites", trialId).then().statusCode(201).extract().header("Location");
        siteId = UUID.fromString(siteLoc.substring(siteLoc.lastIndexOf('/') + 1));

        String enrollLoc = given().contentType("application/json")
                .body("{\"patientId\":\"PAT-LAB-001\"}")
                .when().post("/trials/{t}/sites/{s}/patients", trialId, siteId).then().statusCode(201).extract().header("Location");
        enrollmentId = UUID.fromString(enrollLoc.substring(enrollLoc.lastIndexOf('/') + 1));
    }

    private String basePath() {
        return "/trials/" + trialId + "/sites/" + siteId + "/patients/" + enrollmentId + "/lab-results";
    }

    @Test
    void post_lab_result_returns_201_and_get_retrieves() {
        String loc = given().contentType("application/json")
                .body("""
                    {"testName":"ALT","value":45.5,"unit":"U/L",
                     "referenceRangeLow":7.0,"referenceRangeHigh":56.0,
                     "abnormalFlag":"NORMAL","specimenType":"BLOOD",
                     "performingLab":"Central Lab","collectedAt":"2026-08-15T09:00:00Z"}
                    """)
                .when().post(basePath()).then().statusCode(201).extract().header("Location");

        given().when().get(loc).then().statusCode(200)
                .body("testName", equalTo("ALT"))
                .body("abnormalFlag", equalTo("NORMAL"))
                .body("specimenType", equalTo("BLOOD"));
    }

    @Test
    void list_lab_results_returns_collection() {
        given().contentType("application/json")
                .body("{\"testName\":\"ALT\",\"value\":45.5,\"unit\":\"U/L\",\"abnormalFlag\":\"NORMAL\",\"specimenType\":\"BLOOD\",\"collectedAt\":\"2026-08-15T09:00:00Z\"}")
                .when().post(basePath()).then().statusCode(201);
        given().contentType("application/json")
                .body("{\"testName\":\"AST\",\"value\":30.0,\"unit\":\"U/L\",\"abnormalFlag\":\"NORMAL\",\"specimenType\":\"BLOOD\",\"collectedAt\":\"2026-08-15T09:30:00Z\"}")
                .when().post(basePath()).then().statusCode(201);

        given().when().get(basePath()).then().statusCode(200).body("size()", equalTo(2));
    }

    @Test
    void post_lab_result_with_critical_flag() {
        String loc = given().contentType("application/json")
                .body("{\"testName\":\"Potassium\",\"value\":6.5,\"unit\":\"mmol/L\",\"abnormalFlag\":\"CRITICAL_HIGH\",\"specimenType\":\"BLOOD\",\"collectedAt\":\"2026-08-15T09:00:00Z\"}")
                .when().post(basePath()).then().statusCode(201).extract().header("Location");

        given().when().get(loc).then().statusCode(200)
                .body("abnormalFlag", equalTo("CRITICAL_HIGH"));
    }
}
