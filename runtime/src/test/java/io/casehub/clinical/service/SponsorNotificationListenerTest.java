package io.casehub.clinical.service;

import io.casehub.clinical.api.ProtocolDeviationResolvedEvent;
import io.casehub.clinical.api.SponsorNotificationRequest;
import io.casehub.clinical.api.SponsorNotifier;
import io.casehub.clinical.api.model.DeviationSeverity;
import io.casehub.clinical.api.model.EscalationRequirement;
import io.casehub.clinical.api.model.PiApprovalStatus;
import io.casehub.clinical.api.model.SiteStatus;
import io.casehub.clinical.api.model.TrialPhase;
import io.casehub.clinical.api.model.TrialStatus;
import io.casehub.clinical.entity.ClinicalTrial;
import io.casehub.clinical.entity.TrialSite;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@QuarkusTest
class SponsorNotificationListenerTest {

    @Inject SponsorNotificationListener listener;
    @InjectMock SponsorNotifier sponsorNotifier;

    private UUID trialId;
    private UUID siteId;

    @BeforeEach
    @Transactional
    void setUp() {
        trialId = UUID.randomUUID();
        siteId = UUID.randomUUID();

        ClinicalTrial trial = new ClinicalTrial();
        trial.id = trialId;
        trial.protocolId = "TEST-001";
        trial.phase = TrialPhase.PHASE_II;
        trial.sponsor = "Test Sponsor";
        trial.targetEnrollment = 10;
        trial.status = TrialStatus.ACTIVE;
        trial.sponsorNotificationConnectorId = "slack";
        trial.sponsorNotificationDestination = "https://hooks.slack.com/test";
        trial.persist();

        TrialSite site = new TrialSite();
        site.id = siteId;
        site.trialId = trialId;
        site.investigatorId = "dr-smith@v1";
        site.status = SiteStatus.ACTIVE;
        site.persist();
    }

    @Test
    void sponsor_notification_event_calls_spi_with_correct_request() {
        ProtocolDeviationResolvedEvent event = new ProtocolDeviationResolvedEvent(
            UUID.randomUUID(), siteId, DeviationSeverity.MAJOR,
            EscalationRequirement.SPONSOR_NOTIFICATION, PiApprovalStatus.ESCALATED,
            "CONSENT_DEVIATION", "dr-smith@v1"
        );

        listener.onDeviationResolved(event);

        ArgumentCaptor<SponsorNotificationRequest> cap =
            ArgumentCaptor.forClass(SponsorNotificationRequest.class);
        verify(sponsorNotifier).notify(cap.capture());
        SponsorNotificationRequest req = cap.getValue();
        assertThat(req.trialId()).isEqualTo(trialId);
        assertThat(req.siteId()).isEqualTo(siteId);
        assertThat(req.deviationId()).isEqualTo(event.deviationId());
        assertThat(req.deviationType()).isEqualTo("CONSENT_DEVIATION");
        assertThat(req.piId()).isEqualTo("dr-smith@v1");
        assertThat(req.terminalStatus()).isEqualTo(PiApprovalStatus.ESCALATED);
        assertThat(req.sponsorNotificationConnectorId()).isEqualTo("slack");
        assertThat(req.sponsorNotificationDestination()).isEqualTo("https://hooks.slack.com/test");
        assertThat(req.severity()).isEqualTo(DeviationSeverity.MAJOR);
    }

    @Test
    void irb_review_escalation_does_not_call_spi() {
        ProtocolDeviationResolvedEvent event = new ProtocolDeviationResolvedEvent(
            UUID.randomUUID(), siteId, DeviationSeverity.CRITICAL,
            EscalationRequirement.IRB_REVIEW, PiApprovalStatus.ESCALATED,
            "PROTOCOL_PROCEDURE", "dr-smith@v1"
        );

        listener.onDeviationResolved(event);

        verifyNoInteractions(sponsorNotifier);
    }

    @Test
    void none_escalation_does_not_call_spi() {
        ProtocolDeviationResolvedEvent event = new ProtocolDeviationResolvedEvent(
            UUID.randomUUID(), siteId, DeviationSeverity.MINOR,
            EscalationRequirement.NONE, PiApprovalStatus.APPROVED,
            "MINOR_DEVIATION", "dr-smith@v1"
        );

        listener.onDeviationResolved(event);

        verifyNoInteractions(sponsorNotifier);
    }

    @Test
    @Transactional
    void both_connector_fields_null_does_not_call_spi() {
        TrialSite site = siteWithTrial(null, null);
        listener.onDeviationResolved(sponsorNotificationEvent(site.id));
        verifyNoInteractions(sponsorNotifier);
    }

    @Test
    @Transactional
    void partial_config_connectorId_null_does_not_call_spi() {
        TrialSite site = siteWithTrial(null, "https://hooks.slack.com/test");
        listener.onDeviationResolved(sponsorNotificationEvent(site.id));
        verifyNoInteractions(sponsorNotifier);
    }

    @Test
    @Transactional
    void partial_config_destination_null_does_not_call_spi() {
        TrialSite site = siteWithTrial("slack", null);
        listener.onDeviationResolved(sponsorNotificationEvent(site.id));
        verifyNoInteractions(sponsorNotifier);
    }

    private TrialSite siteWithTrial(String connectorId, String destination) {
        UUID newTrialId = UUID.randomUUID();
        UUID newSiteId = UUID.randomUUID();

        ClinicalTrial trial = new ClinicalTrial();
        trial.id = newTrialId;
        trial.protocolId = "TEST-" + newTrialId.toString().substring(0, 8);
        trial.phase = TrialPhase.PHASE_I;
        trial.sponsor = "S";
        trial.targetEnrollment = 1;
        trial.status = TrialStatus.PLANNING;
        trial.sponsorNotificationConnectorId = connectorId;
        trial.sponsorNotificationDestination = destination;
        trial.persist();

        TrialSite site = new TrialSite();
        site.id = newSiteId;
        site.trialId = newTrialId;
        site.investigatorId = "x";
        site.status = SiteStatus.PENDING;
        site.persist();

        return site;
    }

    private ProtocolDeviationResolvedEvent sponsorNotificationEvent(UUID siteId) {
        return new ProtocolDeviationResolvedEvent(
            UUID.randomUUID(), siteId, DeviationSeverity.MAJOR,
            EscalationRequirement.SPONSOR_NOTIFICATION, PiApprovalStatus.ESCALATED,
            "CONSENT_DEVIATION", "dr-smith@v1"
        );
    }
}
