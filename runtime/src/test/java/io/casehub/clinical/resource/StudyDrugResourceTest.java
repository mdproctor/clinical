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
class StudyDrugResourceTest {

    @Inject FixedCurrentPrincipal principal;
    private UUID trialId, siteId, enrollmentId;

    @AfterEach void resetPrincipal() { principal.reset(); }

    @BeforeEach
    void setup() {
        String trialLoc = given().contentType("application/json")
                .body("{\"protocolId\":\"DRG-" + UUID.randomUUID() + "\",\"phase\":\"PHASE_III\",\"sponsor\":\"S\",\"targetEnrollment\":10}")
                .when().post("/trials").then().statusCode(201).extract().header("Location");
        trialId = UUID.fromString(trialLoc.substring(trialLoc.lastIndexOf('/') + 1));
        String siteLoc = given().contentType("application/json").body("{\"investigatorId\":\"pi-drg\"}")
                .when().post("/trials/{t}/sites", trialId).then().statusCode(201).extract().header("Location");
        siteId = UUID.fromString(siteLoc.substring(siteLoc.lastIndexOf('/') + 1));
        String enrollLoc = given().contentType("application/json").body("{\"patientId\":\"PAT-DRG-001\"}")
                .when().post("/trials/{t}/sites/{s}/patients", trialId, siteId).then().statusCode(201).extract().header("Location");
        enrollmentId = UUID.fromString(enrollLoc.substring(enrollLoc.lastIndexOf('/') + 1));
    }

    private String basePath() {
        return "/trials/" + trialId + "/sites/" + siteId + "/patients/" + enrollmentId + "/study-drug";
    }

    @Test
    void post_drug_admin_returns_201_and_get_retrieves() {
        String loc = given().contentType("application/json")
                .body("""
                    {"drugName":"Pembrolizumab","dose":"200","unit":"mg","route":"IV",
                     "administeredAt":"2026-08-15T10:00:00Z","administeredBy":"nurse-001",
                     "batchNumber":"PEM-2026-0815","status":"ADMINISTERED"}
                    """)
                .when().post(basePath()).then().statusCode(201).extract().header("Location");
        given().when().get(loc).then().statusCode(200)
                .body("drugName", equalTo("Pembrolizumab"))
                .body("route", equalTo("IV"))
                .body("status", equalTo("ADMINISTERED"))
                .body("batchNumber", equalTo("PEM-2026-0815"));
    }

    @Test
    void list_drug_admins_returns_collection() {
        given().contentType("application/json")
                .body("{\"drugName\":\"DrugX\",\"dose\":\"100\",\"unit\":\"mg\",\"route\":\"IV\",\"administeredAt\":\"2026-08-15T10:00:00Z\",\"administeredBy\":\"nurse-001\",\"status\":\"ADMINISTERED\"}")
                .when().post(basePath()).then().statusCode(201);
        given().contentType("application/json")
                .body("{\"drugName\":\"DrugX\",\"dose\":\"100\",\"unit\":\"mg\",\"route\":\"IV\",\"administeredAt\":\"2026-08-22T10:00:00Z\",\"administeredBy\":\"nurse-002\",\"status\":\"ADMINISTERED\"}")
                .when().post(basePath()).then().statusCode(201);
        given().when().get(basePath()).then().statusCode(200).body("size()", equalTo(2));
    }
}
