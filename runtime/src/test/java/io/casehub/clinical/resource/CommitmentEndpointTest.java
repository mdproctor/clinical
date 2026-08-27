package io.casehub.clinical.resource;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

import io.casehub.clinical.api.ClinicalGroups;
import io.casehub.clinical.api.model.DeviationSeverity;
import io.casehub.clinical.api.model.PiApprovalStatus;
import io.casehub.clinical.entity.ClinicalTrial;
import io.casehub.clinical.entity.ProtocolDeviation;
import io.casehub.clinical.entity.TrialSite;
import io.casehub.platform.testing.FixedCurrentPrincipal;
import io.casehub.qhorus.api.message.Commitment;
import io.casehub.qhorus.api.message.CommitmentState;
import io.casehub.qhorus.api.store.CommitmentReader;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@QuarkusTest
@TestSecurity(user = "test-actor", roles = { ClinicalGroups.SPONSOR, ClinicalGroups.INVESTIGATOR, ClinicalGroups.COORDINATOR })
public class CommitmentEndpointTest {

    @Inject FixedCurrentPrincipal principal;
    @InjectMock CommitmentReader commitmentReader;

    private UUID trialId;
    private UUID deviationId;

    @BeforeEach
    @Transactional
    void setup() {
        trialId = UUID.randomUUID();
        ClinicalTrial trial = new ClinicalTrial();
        trial.id = trialId;
        trial.protocolId = "TEST-COMMITMENT";
        trial.phase = io.casehub.clinical.api.model.TrialPhase.PHASE_III;
        trial.sponsor = "Test Sponsor";
        trial.tenantId = principal.tenancyId();
        trial.persist();

        UUID siteId = UUID.randomUUID();
        TrialSite site = new TrialSite();
        site.id = siteId;
        site.trialId = trialId;
        site.investigatorId = "pi-test";
        site.tenantId = principal.tenancyId();
        site.persist();

        deviationId = UUID.randomUUID();
        ProtocolDeviation dev = new ProtocolDeviation();
        dev.id = deviationId;
        dev.siteId = siteId;
        dev.deviationType = "DOSING_ERROR";
        dev.severity = DeviationSeverity.CRITICAL;
        dev.piApprovalStatus = PiApprovalStatus.PENDING;
        dev.piCommandChannelName = "clinical/deviation/dev-test-123/pi-oversight";
        dev.commandedAt = Instant.now();
        dev.tenantId = principal.tenancyId();
        dev.persist();

        Commitment commitment = Commitment.builder()
                .id(UUID.randomUUID())
                .correlationId(deviationId.toString())
                .channelId(UUID.randomUUID())
                .messageType(io.casehub.qhorus.api.message.MessageType.COMMAND)
                .requester("clinical-service")
                .obligor("pi-test")
                .state(CommitmentState.OPEN)
                .tenancyId(principal.tenancyId())
                .createdAt(Instant.now())
                .build();
        when(commitmentReader.findByCorrelationId(deviationId.toString())).thenReturn(Optional.of(commitment));
    }

    @Test
    void returns404WhenDeviationNotFound() {
        given()
            .when().get("/trials/{trialId}/deviations/{devId}/commitment",
                trialId, UUID.randomUUID())
            .then()
            .statusCode(404);
    }

    @Test
    void returns200WithCommitmentData() {
        given()
            .when().get("/trials/{trialId}/deviations/{devId}/commitment",
                trialId, deviationId)
            .then()
            .statusCode(200)
            .body("id", notNullValue())
            .body("currentStage", is("COMMANDED"))
            .body("stages", notNullValue())
            .body("stages.size()", is(4));
    }

    @Test
    void returns404WhenTrialIdMismatch() {
        UUID wrongTrialId = UUID.randomUUID();
        given()
            .when().get("/trials/{trialId}/deviations/{devId}/commitment",
                wrongTrialId, deviationId)
            .then()
            .statusCode(404);
    }
}
