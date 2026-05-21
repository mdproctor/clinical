package io.casehub.clinical.resource;

import io.casehub.clinical.api.model.TrialPhase;
import io.casehub.clinical.api.model.TrialStatus;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class TrialResourceTest {

    @Test
    void post_trial_returns_201_with_location() {
        given()
            .contentType("application/json")
            .body("""
                {
                  "protocolId": "ONCOL-001",
                  "phase": "PHASE_III",
                  "sponsor": "Acme Pharma",
                  "targetEnrollment": 150
                }
                """)
        .when()
            .post("/trials")
        .then()
            .statusCode(201)
            .header("Location", containsString("/trials/"));
    }

    @Test
    void get_trial_returns_200_with_fields() {
        String location =
            given()
                .contentType("application/json")
                .body("""
                    {
                      "protocolId": "ONCOL-002",
                      "phase": "PHASE_II",
                      "sponsor": "BioTest",
                      "targetEnrollment": 50
                    }
                    """)
            .when()
                .post("/trials")
            .then()
                .statusCode(201)
                .extract().header("Location");

        given()
        .when()
            .get(location)
        .then()
            .statusCode(200)
            .body("protocolId", equalTo("ONCOL-002"))
            .body("phase", equalTo("PHASE_II"))
            .body("sponsor", equalTo("BioTest"))
            .body("targetEnrollment", equalTo(50))
            .body("status", equalTo("PLANNING"));
    }

    @Test
    void get_unknown_trial_returns_404() {
        given()
        .when()
            .get("/trials/" + UUID.randomUUID())
        .then()
            .statusCode(404);
    }

    @Test
    void post_trial_missing_protocol_id_returns_400() {
        given()
            .contentType("application/json")
            .body("""
                {
                  "phase": "PHASE_III",
                  "sponsor": "Acme",
                  "targetEnrollment": 100
                }
                """)
        .when()
            .post("/trials")
        .then()
            .statusCode(400);
    }

    @Test
    void post_trial_missing_phase_returns_400() {
        given()
            .contentType("application/json")
            .body("""
                {
                  "protocolId": "ONCOL-NO-PHASE",
                  "sponsor": "Acme",
                  "targetEnrollment": 100
                }
                """)
        .when()
            .post("/trials")
        .then()
            .statusCode(400);
    }

    @Test
    void register_with_sponsor_config_persists_connector_fields() {
        String body = """
            {
              "protocolId": "ONCO-2026-001",
              "phase": "PHASE_II",
              "sponsor": "Pfizer",
              "targetEnrollment": 50,
              "sponsorNotificationConnectorId": "slack",
              "sponsorNotificationDestination": "https://hooks.slack.com/T000/B000/xxx"
            }
            """;

        String location = given()
            .contentType("application/json")
            .body(body)
            .when().post("/trials")
            .then().statusCode(201)
            .extract().header("Location");

        String id = location.substring(location.lastIndexOf('/') + 1);
        given()
            .when().get("/trials/" + id)
            .then().statusCode(200)
            .body("sponsorNotificationConnectorId", equalTo("slack"))
            .body("sponsorNotificationDestination", equalTo("https://hooks.slack.com/T000/B000/xxx"));
    }
}
