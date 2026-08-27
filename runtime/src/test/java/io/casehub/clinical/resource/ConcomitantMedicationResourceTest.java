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
class ConcomitantMedicationResourceTest {

    @Inject FixedCurrentPrincipal principal;
    private UUID trialId, siteId, enrollmentId;

    @AfterEach void resetPrincipal() { principal.reset(); }

    @BeforeEach
    void setup() {
        String trialLoc = given().contentType("application/json")
                .body("{\"protocolId\":\"MED-" + UUID.randomUUID() + "\",\"phase\":\"PHASE_II\",\"sponsor\":\"S\",\"targetEnrollment\":10}")
                .when().post("/trials").then().statusCode(201).extract().header("Location");
        trialId = UUID.fromString(trialLoc.substring(trialLoc.lastIndexOf('/') + 1));
        String siteLoc = given().contentType("application/json").body("{\"investigatorId\":\"pi-med\"}")
                .when().post("/trials/{t}/sites", trialId).then().statusCode(201).extract().header("Location");
        siteId = UUID.fromString(siteLoc.substring(siteLoc.lastIndexOf('/') + 1));
        String enrollLoc = given().contentType("application/json").body("{\"patientId\":\"PAT-MED-001\"}")
                .when().post("/trials/{t}/sites/{s}/patients", trialId, siteId).then().statusCode(201).extract().header("Location");
        enrollmentId = UUID.fromString(enrollLoc.substring(enrollLoc.lastIndexOf('/') + 1));
    }

    private String basePath() {
        return "/trials/" + trialId + "/sites/" + siteId + "/patients/" + enrollmentId + "/medications";
    }

    @Test
    void post_medication_returns_201_and_get_retrieves() {
        String loc = given().contentType("application/json")
                .body("""
                    {"medicationName":"Metformin","indication":"Type 2 Diabetes",
                     "dose":"500","unit":"mg","route":"ORAL","frequency":"TWICE_DAILY",
                     "startDate":"2026-08-15","ongoing":true}
                    """)
                .when().post(basePath()).then().statusCode(201).extract().header("Location");
        given().when().get(loc).then().statusCode(200)
                .body("medicationName", equalTo("Metformin"))
                .body("route", equalTo("ORAL"))
                .body("ongoing", equalTo(true));
    }

    @Test
    void patch_medication_discontinues() {
        String loc = given().contentType("application/json")
                .body("{\"medicationName\":\"Aspirin\",\"dose\":\"81\",\"unit\":\"mg\",\"route\":\"ORAL\",\"frequency\":\"ONCE_DAILY\",\"startDate\":\"2026-08-01\",\"ongoing\":true}")
                .when().post(basePath()).then().statusCode(201).extract().header("Location");

        given().contentType("application/json")
                .body("{\"endDate\":\"2026-08-15\",\"ongoing\":false}")
                .when().patch(loc).then().statusCode(200)
                .body("ongoing", equalTo(false))
                .body("endDate", equalTo("2026-08-15"));
    }

    @Test
    void list_medications_returns_collection() {
        given().contentType("application/json")
                .body("{\"medicationName\":\"Drug A\",\"dose\":\"10\",\"unit\":\"mg\",\"route\":\"ORAL\",\"frequency\":\"ONCE_DAILY\",\"startDate\":\"2026-08-01\",\"ongoing\":true}")
                .when().post(basePath()).then().statusCode(201);
        given().contentType("application/json")
                .body("{\"medicationName\":\"Drug B\",\"dose\":\"20\",\"unit\":\"mg\",\"route\":\"IV\",\"frequency\":\"WEEKLY\",\"startDate\":\"2026-08-10\",\"ongoing\":true}")
                .when().post(basePath()).then().statusCode(201);
        given().when().get(basePath()).then().statusCode(200).body("size()", equalTo(2));
    }
}
