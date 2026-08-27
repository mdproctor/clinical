package io.casehub.clinical.resource;

import io.casehub.clinical.api.ClinicalGroups;
import io.casehub.platform.testing.FixedCurrentPrincipal;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
@TestSecurity(user = "test-actor", roles = {ClinicalGroups.SPONSOR, ClinicalGroups.INVESTIGATOR, ClinicalGroups.COORDINATOR})
class TrialListResourceTest {

    @Inject FixedCurrentPrincipal principal;

    @AfterEach
    void resetPrincipal() { principal.reset(); }

    @Test
    void get_trials_returns_list_including_created_trial() {
        String protocolId = "LIST-TEST-" + UUID.randomUUID();
        given().contentType("application/json")
                .body("{\"protocolId\":\"" + protocolId + "\",\"phase\":\"PHASE_III\",\"sponsor\":\"Acme\",\"targetEnrollment\":100}")
                .when().post("/trials").then().statusCode(201);

        given().when().get("/trials")
                .then().statusCode(200)
                .body("size()", greaterThanOrEqualTo(1))
                .body("find { it.protocolId == '" + protocolId + "' }.phase", equalTo("PHASE_III"))
                .body("find { it.protocolId == '" + protocolId + "' }.sponsor", equalTo("Acme"))
                .body("find { it.protocolId == '" + protocolId + "' }.status", equalTo("PLANNING"));
    }

    @Test
    void get_trials_filters_by_tenant() {
        given().contentType("application/json")
                .body("{\"protocolId\":\"TENANT-" + UUID.randomUUID() + "\",\"phase\":\"PHASE_I\",\"sponsor\":\"T\",\"targetEnrollment\":5}")
                .when().post("/trials").then().statusCode(201);

        principal.setTenancyId("isolated-tenant-" + UUID.randomUUID());
        given().when().get("/trials")
                .then().statusCode(200).body("size()", equalTo(0));
    }

    @Test
    void get_trials_returns_id_and_targetEnrollment() {
        String protocolId = "FIELD-TEST-" + UUID.randomUUID();
        given().contentType("application/json")
                .body("{\"protocolId\":\"" + protocolId + "\",\"phase\":\"PHASE_II\",\"sponsor\":\"S\",\"targetEnrollment\":42}")
                .when().post("/trials").then().statusCode(201);

        given().when().get("/trials")
                .then().statusCode(200)
                .body("find { it.protocolId == '" + protocolId + "' }.id", notNullValue())
                .body("find { it.protocolId == '" + protocolId + "' }.targetEnrollment", equalTo(42));
    }
}
