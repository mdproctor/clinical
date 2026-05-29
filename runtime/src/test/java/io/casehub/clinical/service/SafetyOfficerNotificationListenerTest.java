package io.casehub.clinical.service;

import io.casehub.clinical.api.AdverseEventReportedEvent;
import io.casehub.clinical.api.SafetyOfficerNotificationRequest;
import io.casehub.clinical.api.SafetyOfficerNotifier;
import io.casehub.clinical.api.model.CtcaeGrade;
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

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;

@QuarkusTest
class SafetyOfficerNotificationListenerTest {

    @Inject SafetyOfficerNotificationListener listener;
    @InjectMock SafetyOfficerNotifier safetyOfficerNotifier;

    private UUID trialId;
    private UUID siteId;

    @BeforeEach
    @Transactional
    void setUp() {
        trialId = UUID.randomUUID();
        siteId = UUID.randomUUID();

        ClinicalTrial trial = new ClinicalTrial();
        trial.id = trialId;
        trial.protocolId = "ONCO-001";
        trial.phase = TrialPhase.PHASE_III;
        trial.sponsor = "Test Pharma";
        trial.targetEnrollment = 100;
        trial.status = TrialStatus.ACTIVE;
        trial.safetyOfficerConnectorId = "slack";
        trial.safetyOfficerDestination = "https://hooks.slack.com/safety-officer";
        trial.persist();

        TrialSite site = new TrialSite();
        site.id = siteId;
        site.trialId = trialId;
        site.investigatorId = "dr-jones@v1";
        site.status = SiteStatus.ACTIVE;
        site.persist();
    }

    @Test
    void grade3_event_calls_spi_with_correct_request() {
        final UUID aeId = UUID.randomUUID();
        final UUID enrollmentId = UUID.randomUUID();
        final AdverseEventReportedEvent event = new AdverseEventReportedEvent(
            aeId, enrollmentId, siteId, CtcaeGrade.GRADE_3, Instant.now());

        listener.onAeReported(event);

        final ArgumentCaptor<SafetyOfficerNotificationRequest> cap =
            ArgumentCaptor.forClass(SafetyOfficerNotificationRequest.class);
        verify(safetyOfficerNotifier).notify(cap.capture());
        final SafetyOfficerNotificationRequest req = cap.getValue();
        assertThat(req.aeId()).isEqualTo(aeId);
        assertThat(req.enrollmentId()).isEqualTo(enrollmentId);
        assertThat(req.siteId()).isEqualTo(siteId);
        assertThat(req.grade()).isEqualTo(CtcaeGrade.GRADE_3);
        assertThat(req.connectorId()).isEqualTo("slack");
        assertThat(req.destination()).isEqualTo("https://hooks.slack.com/safety-officer");
    }

    @Test
    void grade5_event_calls_spi_with_grade5_in_request() {
        final AdverseEventReportedEvent event = new AdverseEventReportedEvent(
            UUID.randomUUID(), UUID.randomUUID(), siteId, CtcaeGrade.GRADE_5, Instant.now());

        listener.onAeReported(event);

        final ArgumentCaptor<SafetyOfficerNotificationRequest> cap =
            ArgumentCaptor.forClass(SafetyOfficerNotificationRequest.class);
        verify(safetyOfficerNotifier).notify(cap.capture());
        assertThat(cap.getValue().grade()).isEqualTo(CtcaeGrade.GRADE_5);
    }

    @Test
    void null_siteId_does_not_call_spi() {
        final AdverseEventReportedEvent event = new AdverseEventReportedEvent(
            UUID.randomUUID(), UUID.randomUUID(), null, CtcaeGrade.GRADE_4, Instant.now());

        listener.onAeReported(event);

        verifyNoInteractions(safetyOfficerNotifier);
    }

    @Test
    @Transactional
    void unknown_site_does_not_call_spi() {
        final AdverseEventReportedEvent event = new AdverseEventReportedEvent(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            CtcaeGrade.GRADE_3, Instant.now());

        listener.onAeReported(event);

        verifyNoInteractions(safetyOfficerNotifier);
    }

    @Test
    @Transactional
    void missing_connector_config_does_not_call_spi() {
        final UUID newTrialId = UUID.randomUUID();
        final UUID newSiteId = UUID.randomUUID();

        final ClinicalTrial trial = new ClinicalTrial();
        trial.id = newTrialId;
        trial.protocolId = "NO-NOTIF-001";
        trial.phase = TrialPhase.PHASE_I;
        trial.sponsor = "S";
        trial.targetEnrollment = 1;
        trial.status = TrialStatus.PLANNING;
        // safety officer config intentionally absent
        trial.persist();

        final TrialSite site = new TrialSite();
        site.id = newSiteId;
        site.trialId = newTrialId;
        site.investigatorId = "x";
        site.status = SiteStatus.PENDING;
        site.persist();

        listener.onAeReported(new AdverseEventReportedEvent(
            UUID.randomUUID(), UUID.randomUUID(), newSiteId,
            CtcaeGrade.GRADE_3, Instant.now()));

        verifyNoInteractions(safetyOfficerNotifier);
    }

    @Test
    @Transactional
    void partial_config_connector_id_null_does_not_call_spi() {
        final TrialSite site = siteWithConfig(null, "https://hooks.slack.com/test");
        listener.onAeReported(aeEvent(site.id));
        verifyNoInteractions(safetyOfficerNotifier);
    }

    @Test
    @Transactional
    void partial_config_destination_null_does_not_call_spi() {
        final TrialSite site = siteWithConfig("slack", null);
        listener.onAeReported(aeEvent(site.id));
        verifyNoInteractions(safetyOfficerNotifier);
    }

    private TrialSite siteWithConfig(final String connectorId, final String destination) {
        final UUID newTrialId = UUID.randomUUID();
        final UUID newSiteId = UUID.randomUUID();

        final ClinicalTrial trial = new ClinicalTrial();
        trial.id = newTrialId;
        trial.protocolId = "T-" + newTrialId.toString().substring(0, 8);
        trial.phase = TrialPhase.PHASE_I;
        trial.sponsor = "S";
        trial.targetEnrollment = 1;
        trial.status = TrialStatus.PLANNING;
        trial.safetyOfficerConnectorId = connectorId;
        trial.safetyOfficerDestination = destination;
        trial.persist();

        final TrialSite site = new TrialSite();
        site.id = newSiteId;
        site.trialId = newTrialId;
        site.investigatorId = "x";
        site.status = SiteStatus.PENDING;
        site.persist();

        return site;
    }

    private AdverseEventReportedEvent aeEvent(final UUID siteId) {
        return new AdverseEventReportedEvent(
            UUID.randomUUID(), UUID.randomUUID(), siteId,
            CtcaeGrade.GRADE_3, Instant.now());
    }
}
