package io.casehub.clinical.service;

import io.casehub.clinical.api.model.*;
import io.casehub.clinical.entity.*;
import io.casehub.qhorus.api.gateway.ChannelRef;
import io.casehub.qhorus.api.gateway.InboundHumanMessage;
import io.casehub.qhorus.runtime.channel.ChannelService;
import io.casehub.qhorus.runtime.gateway.ChannelGateway;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import io.quarkus.narayana.jta.QuarkusTransaction;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import java.time.Duration;

/**
 * Full channel-flow integration test: receiveHumanMessage → MessageReceivedEvent CDI event → PiResponseListener.
 * Enabled when casehubio/qhorus#153 (MessageReceivedEvent CDI hook) shipped.
 */
@QuarkusTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PiResponseListenerIntegrationTest {

    @Inject ProtocolDeviationService deviationService;
    @Inject ChannelService channelService;
    @Inject ChannelGateway channelGateway;

    private UUID siteId;
    private UUID minorDeviationId;
    private UUID criticalDeviationId;

    @BeforeAll
    @Transactional
    void setup() {
        UUID trialId = UUID.randomUUID();
        siteId = UUID.randomUUID();
        ClinicalTrial trial = new ClinicalTrial();
        trial.id = trialId; trial.protocolId = "INT-" + trialId; trial.phase = TrialPhase.PHASE_II;
        trial.sponsor = "S"; trial.targetEnrollment = 10; trial.status = TrialStatus.ACTIVE;
        trial.persist();
        TrialSite site = new TrialSite();
        site.id = siteId; site.trialId = trialId; site.investigatorId = "pi-int";
        site.persist();
    }

    @Test
    @Order(1)
    @Transactional
    void reportMinorDeviationReachesCommandedState() {
        ProtocolDeviation dev = new ProtocolDeviation();
        dev.id = UUID.randomUUID();
        dev.siteId = siteId;
        dev.deviationType = "sample-window";
        dev.severity = DeviationSeverity.MINOR;
        minorDeviationId = dev.id;
        deviationService.reportDeviation(dev);

        ProtocolDeviation loaded = ProtocolDeviation.findById(dev.id);
        assertThat(loaded.piApprovalStatus).isEqualTo(PiApprovalStatus.COMMANDED);
        assertThat(loaded.piCommandChannelName).contains("/pi-oversight");
    }

    @Test
    @Order(2)
    @Transactional
    void reportCriticalDeviationReachesCommandedState() {
        ProtocolDeviation dev = new ProtocolDeviation();
        dev.id = UUID.randomUUID();
        dev.siteId = siteId;
        dev.deviationType = "eligibility-breach";
        dev.severity = DeviationSeverity.CRITICAL;
        criticalDeviationId = dev.id;
        deviationService.reportDeviation(dev);

        ProtocolDeviation loaded = ProtocolDeviation.findById(dev.id);
        assertThat(loaded.piApprovalStatus).isEqualTo(PiApprovalStatus.COMMANDED);
    }

    @Test
    @Order(3)
    void piApprovalViaChannelGateway_minorDeviationMovesToApproved() throws Exception {
        assertThat(minorDeviationId).as("set by Order(1)").isNotNull();

        ProtocolDeviation dev = ProtocolDeviation.findById(minorDeviationId);
        var channelRef = channelService.findByName(dev.piCommandChannelName)
            .map(c -> new ChannelRef(c.id, c.name))
            .orElseThrow(() -> new AssertionError("channel not found: " + dev.piCommandChannelName));

        channelGateway.receiveHumanMessage(channelRef,
            new InboundHumanMessage("pi-int", "{\"decision\":\"APPROVED\"}", Instant.now(),
                Map.of(), minorDeviationId.toString()));

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
            QuarkusTransaction.requiringNew().call(() -> {
                ProtocolDeviation loaded = ProtocolDeviation.findById(minorDeviationId);
                assertThat(loaded.piApprovalStatus).isEqualTo(PiApprovalStatus.APPROVED);
                return null;
            }));
    }

    @Test
    @Order(4)
    void piApprovalViaChannelGateway_criticalDeviationMovesToEscalated() throws Exception {
        assertThat(criticalDeviationId).as("set by Order(2)").isNotNull();

        ProtocolDeviation dev = ProtocolDeviation.findById(criticalDeviationId);
        var channelRef = channelService.findByName(dev.piCommandChannelName)
            .map(c -> new ChannelRef(c.id, c.name))
            .orElseThrow(() -> new AssertionError("channel not found: " + dev.piCommandChannelName));

        channelGateway.receiveHumanMessage(channelRef,
            new InboundHumanMessage("pi-int", "{\"decision\":\"APPROVED\"}", Instant.now(),
                Map.of(), criticalDeviationId.toString()));

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
            QuarkusTransaction.requiringNew().call(() -> {
                ProtocolDeviation loaded = ProtocolDeviation.findById(criticalDeviationId);
                assertThat(loaded.piApprovalStatus).isEqualTo(PiApprovalStatus.ESCALATED);
                return null;
            }));
    }
}
