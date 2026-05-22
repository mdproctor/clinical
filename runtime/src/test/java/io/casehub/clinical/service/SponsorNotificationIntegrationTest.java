package io.casehub.clinical.service;

import io.casehub.clinical.api.model.*;
import io.casehub.clinical.entity.*;
import io.casehub.qhorus.api.message.MessageType;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;

@QuarkusTest
class SponsorNotificationIntegrationTest {

    @Inject PiResponseListener piResponseListener;
    @Inject TestSlackConnector slackConnector;
    @InjectMock DeviationLedgerWriter ledgerWriter;

    private UUID deviationId;
    private String channelName;

    @BeforeEach
    @Transactional
    void setUp() {
        slackConnector.reset();

        // Suppress all ledger writes
        doNothing().when(ledgerWriter).writeResolutionEntry(any(), any(), any(), any(), any());
        doNothing().when(ledgerWriter).writeSponsorNotifiedEntry(any(), any(Instant.class), any(Boolean.class));

        UUID trialId = UUID.randomUUID();
        UUID siteId = UUID.randomUUID();
        deviationId = UUID.randomUUID();
        channelName = "clinical/deviation/" + deviationId + "/pi-oversight";

        ClinicalTrial trial = new ClinicalTrial();
        trial.id = trialId;
        trial.protocolId = "ONCO-001";
        trial.phase = TrialPhase.PHASE_III;
        trial.sponsor = "Roche";
        trial.targetEnrollment = 100;
        trial.status = TrialStatus.ACTIVE;
        trial.sponsorNotificationConnectorId = "slack";
        trial.sponsorNotificationDestination = "https://hooks.slack.com/integration-test";
        trial.persist();

        TrialSite site = new TrialSite();
        site.id = siteId;
        site.trialId = trialId;
        site.investigatorId = "dr-jones@v1";
        site.status = SiteStatus.ACTIVE;
        site.persist();

        ProtocolDeviation dev = new ProtocolDeviation();
        dev.id = deviationId;
        dev.siteId = siteId;
        dev.deviationType = "INFORMED_CONSENT";
        dev.severity = DeviationSeverity.MAJOR;
        dev.escalationRequirement = EscalationRequirement.SPONSOR_NOTIFICATION;
        dev.piApprovalStatus = PiApprovalStatus.COMMANDED;
        dev.piCommandChannelName = channelName;
        dev.commandedAt = Instant.now();
        dev.responseDeadline = Instant.now().plusSeconds(3600);
        dev.persist();
    }

    @Test
    void major_deviation_pi_approval_triggers_slack_notification() {
        piResponseListener.process(channelName, MessageType.DONE, "dr-jones@v1");

        Awaitility.await().atMost(Duration.ofSeconds(5)).until(() ->
            !slackConnector.sent().isEmpty()
        );

        assertThat(slackConnector.sent()).hasSize(1);
        assertThat(slackConnector.sent().get(0).destination())
            .isEqualTo("https://hooks.slack.com/integration-test");
        assertThat(slackConnector.sent().get(0).body())
            .contains("INFORMED_CONSENT")
            .contains("dr-jones@v1")
            .contains("corrective action committed");
    }

    @Test
    void major_deviation_pi_rejection_also_triggers_sponsor_notification() {
        piResponseListener.process(channelName, MessageType.DECLINE, "dr-jones@v1");

        Awaitility.await().atMost(Duration.ofSeconds(5)).until(() ->
            !slackConnector.sent().isEmpty()
        );

        assertThat(slackConnector.sent()).hasSize(1);
        assertThat(slackConnector.sent().get(0).body()).contains("refused to authorise");
    }

    @Test
    void connector_delivery_failure_does_not_propagate_exception() {
        slackConnector.setShouldThrow(true);

        // Must complete without exception even when connector throws
        piResponseListener.process(channelName, MessageType.DONE, "dr-jones@v1");

        // Give async events time to process; verify ledger write was called (with delivered=false)
        Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
            Mockito.verify(ledgerWriter, Mockito.atLeastOnce())
                .writeSponsorNotifiedEntry(any(), any(Instant.class), Mockito.eq(false))
        );
    }
}
