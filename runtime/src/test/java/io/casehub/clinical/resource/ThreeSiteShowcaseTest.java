package io.casehub.clinical.resource;

import io.casehub.api.model.CaseStatus;
import io.casehub.clinical.api.ClinicalGroups;
import io.casehub.clinical.entity.ClinicalTrial;
import io.casehub.clinical.entity.TrialSite;
import io.casehub.clinical.ledger.ProtocolAmendmentLedgerEntry;
import io.casehub.clinical.support.EngineStateCleaner;
import io.casehub.clinical.support.WorkItemQueries;
import io.casehub.engine.common.spi.cache.CaseInstanceCache;
import io.casehub.ledger.api.spi.LedgerEntryRepository;
import io.casehub.platform.testing.FixedCurrentPrincipal;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.*;

/**
 * §7.4 Showcase scenario — 3-site oncology trial demonstrating all completed layers.
 *
 * <ul>
 *   <li>Site A: eligibility screening with marginal criteria → IRB 72h consultation (Layer 9)</li>
 *   <li>Site B: Grade 3 AE → 24h SLA escalation + IND expedited report (Layers 2+7)</li>
 *   <li>Site C: protocol amendment → advisor stub (LlmPlanningStrategy pending engine#101) → APPROVED (Layer 9)</li>
 * </ul>
 *
 * <p>Cross-cutting: FDA Merkle audit trail independently verifiable for Sites A and B.
 * Comparison: see docs/comparison/clinicalagent.md
 */
@QuarkusTest
@TestSecurity(user = "test-actor", roles = {ClinicalGroups.SPONSOR, ClinicalGroups.INVESTIGATOR, ClinicalGroups.COORDINATOR})
@org.junit.jupiter.api.Tag("showcase")
class ThreeSiteShowcaseTest {

    @Inject WorkItemQueries workItemQueries;
    @Inject LedgerEntryRepository ledgerRepo;
    @Inject FixedCurrentPrincipal principal;
    @Inject CaseInstanceCache caseInstanceCache;
    @Inject EngineStateCleaner engineStateCleaner;

    UUID trialId, siteAId, siteBId, siteCId;

    /** Captured just before the first REST call — used to filter out WorkItems from prior tests. */
    Instant testStartedAt;

    @BeforeEach
    void setup() {
        engineStateCleaner.cancelAllAndClear();
        testStartedAt = Instant.now();

        // ── 2. Assign IDs for this test ──
        trialId = UUID.randomUUID();
        siteAId = UUID.randomUUID();
        siteBId = UUID.randomUUID();
        siteCId = UUID.randomUUID();

        // ── 3. Persist test data in its own transaction ──
        persistTestData();
    }

    @Transactional
    void persistTestData() {
        ClinicalTrial trial = new ClinicalTrial();
        trial.id = trialId;
        trial.protocolId = "ONCOL-SHOWCASE-2026-" + UUID.randomUUID();
        trial.phase = io.casehub.clinical.api.model.TrialPhase.PHASE_III;
        trial.sponsor = "Acme Oncology";
        trial.targetEnrollment = 300;
        trial.tenantId = principal.tenancyId();
        trial.persist();

        addSite(siteAId, trialId, "pi-site-a-001");
        addSite(siteBId, trialId, "pi-site-b-002");
        addSite(siteCId, trialId, "pi-site-c-003");
    }

    private void addSite(UUID siteId, UUID trialId, String investigatorId) {
        TrialSite site = new TrialSite();
        site.id = siteId;
        site.trialId = trialId;
        site.investigatorId = investigatorId;
        site.tenantId = principal.tenancyId();
        site.persist();
    }

    @Test
    void three_site_oncology_showcase() {
        // ── SITE A: Eligibility screening ────────────────────────────────────────
        String patientALoc = given()
            .contentType("application/json")
            .body("{\"patientId\": \"PATIENT-A-001\"}")
        .when()
            .post("/trials/{t}/sites/{s}/patients", trialId, siteAId)
        .then().statusCode(201).extract().header("Location");
        UUID enrollmentA = extractId(patientALoc);

        given()
            .contentType("application/json")
            .body("""
                { "criteria": [
                  { "id": "criterion-7", "met": false, "marginal": true },
                  { "id": "criterion-11", "met": false, "marginal": true }
                ]}
                """)
        .when()
            .post("/trials/{t}/sites/{s}/patients/{e}/screen", trialId, siteAId, enrollmentA)
        .then()
            .statusCode(200)
            .body("enrollmentStatus", equalTo("SCREENING"))
            .body("screeningResult", equalTo("MARGINAL"));

        // IRB consultation WorkItem created by engine case (async).
        // Filter by enrollment ID in payload to distinguish from deviation-review irb-committee
        // WorkItems that may be created by ClinicalLayerComplianceTest running in the same JVM.
        String enrollmentAStr = enrollmentA.toString();
        await().atMost(15, SECONDS).untilAsserted(() ->
            assertThat(workItemQueries.scanAll().stream()
                .anyMatch(wi -> wi.candidateGroups() != null
                    && "irb-committee".equals(wi.candidateGroups().trim())
                    && wi.payload() != null && wi.payload().contains(enrollmentAStr)))
            .isTrue()
        );

        workItemQueries.scanAll().stream()
            .filter(wi -> wi.candidateGroups() != null && "irb-committee".equals(wi.candidateGroups().trim())
                && wi.payload() != null && wi.payload().contains(enrollmentAStr))
            .findFirst()
            .ifPresent(wi -> {
                assertThat(wi.expiresAt()).as("IRB consultation WorkItem must have an expiry (PT72H)").isNotNull();
                assertThat(Duration.between(Instant.now(), wi.expiresAt()).toHours())
                    .isLessThanOrEqualTo(73L);
            });

        // Site A ledger chain verifiable (Merkle proof)
        given().when()
            .get("/trials/{t}/sites/{s}/patients/{e}/ledger/verify", trialId, siteAId, enrollmentA)
        .then()
            .statusCode(200)
            .body("valid", equalTo(true))
            .body("merkleRoot", notNullValue());

        // ── SITE B: Adverse event escalation ─────────────────────────────────────
        String patientBLoc = given()
            .contentType("application/json")
            .body("{\"patientId\": \"PATIENT-B-001\"}")
        .when()
            .post("/trials/{t}/sites/{s}/patients", trialId, siteBId)
        .then().statusCode(201).extract().header("Location");
        UUID enrollmentB = extractId(patientBLoc);

        String aeLoc = given()
            .contentType("application/json")
            .body("""
                {"grade":"GRADE_3","occurredAt":"%s","unexpected":true}
                """.formatted(Instant.now().minus(Duration.ofHours(2))))
        .when()
            .post("/trials/{t}/sites/{s}/patients/{e}/adverse-events", trialId, siteBId, enrollmentB)
        .then()
            .statusCode(201)
            .body("workItemId", nullValue())
            .extract().header("Location");
        UUID aeId = extractId(aeLoc);

        // SLA deadline within 24h
        String slaStr = given().when()
            .get("/trials/{t}/sites/{s}/patients/{e}/adverse-events/{ae}",
                trialId, siteBId, enrollmentB, aeId)
        .then().statusCode(200).extract().path("slaDeadline");
        assertThat(Duration.between(Instant.now(), Instant.parse(slaStr)).toHours())
            .isBetween(23L, 24L);

        // IND expedited safety report triggered (async observer — RegulatorySubmissionCaseService)
        await().atMost(15, SECONDS).untilAsserted(() ->
            given().when()
                .get("/trials/{t}/sites/{s}/patients/{e}/adverse-events/{ae}",
                    trialId, siteBId, enrollmentB, aeId)
            .then()
                .statusCode(200)
                .body("regulatorySubmissionStatus", equalTo("PENDING"))
        );

        // FDA Merkle proof — independent verification without server access
        given().when()
            .get("/trials/{t}/sites/{s}/patients/{e}/ledger/verify", trialId, siteBId, enrollmentB)
        .then()
            .statusCode(200)
            .body("valid", equalTo(true))
            .body("merkleRoot", notNullValue());

        // ── SITE C: Protocol amendment ────────────────────────────────────────────
        String amendmentLoc = given()
            .contentType("application/json")
            .body("{\"proposedChange\": \"Dose escalation amendment v2\"}")
        .when()
            .post("/trials/{t}/amendments", trialId)
        .then()
            .statusCode(201)
            .body("status", equalTo("PROPOSED"))
            .extract().header("Location");
        UUID amendmentId = extractId(amendmentLoc);

        // Advisor stub (DefaultProtocolAmendmentAdvisor → PROCEED) processes asynchronously
        await().atMost(15, SECONDS).untilAsserted(() ->
            given().when()
                .get("/trials/{t}/amendments/{id}", trialId, amendmentId)
            .then()
                .statusCode(200)
                .body("status", equalTo("APPROVED"))
        );

        // Two ledger entries: proposal + resolution
        long amendmentEntries = ledgerRepo.findBySubjectId(amendmentId, "default")
            .stream()
            .filter(e -> e instanceof ProtocolAmendmentLedgerEntry)
            .count();
        assertThat(amendmentEntries).isGreaterThanOrEqualTo(2);
    }

    private UUID extractId(String location) {
        return UUID.fromString(location.substring(location.lastIndexOf('/') + 1));
    }
}
