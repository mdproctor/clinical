package io.casehub.clinical.service;

import io.casehub.clinical.api.SponsorNotificationRequest;
import io.casehub.clinical.api.model.DeviationSeverity;
import io.casehub.clinical.api.model.EscalationRequirement;
import io.casehub.clinical.api.model.PiApprovalStatus;
import io.casehub.clinical.entity.ProtocolDeviation;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@QuarkusTest
class DefaultSponsorNotifierTest {

    @Inject DefaultSponsorNotifier notifier;
    @Inject TestSlackConnector slackConnector;
    @InjectMock DeviationLedgerWriter ledgerWriter;

    private UUID deviationId;
    private UUID siteId;

    @BeforeEach
    @Transactional
    void setUp() {
        slackConnector.reset();

        deviationId = UUID.randomUUID();
        siteId = UUID.randomUUID();

        ProtocolDeviation dev = new ProtocolDeviation();
        dev.id = deviationId;
        dev.siteId = siteId;
        dev.deviationType = "CONSENT_DEVIATION";
        dev.severity = DeviationSeverity.MAJOR;
        dev.escalationRequirement = EscalationRequirement.SPONSOR_NOTIFICATION;
        dev.piApprovalStatus = PiApprovalStatus.COMMANDED;
        dev.commandedAt = Instant.now();
        dev.responseDeadline = Instant.now().plusSeconds(3600);
        dev.persist();
    }

    @Test
    void escalated_notification_sends_to_connector_and_writes_delivered_ledger_entry() {
        notifier.notify(request(PiApprovalStatus.ESCALATED, "dr-smith@v1", "slack"));

        assertThat(slackConnector.sent()).hasSize(1);
        assertThat(slackConnector.sent().get(0).body())
            .contains("CONSENT_DEVIATION")
            .contains("dr-smith@v1")
            .contains("corrective action committed");
        verify(ledgerWriter).writeSponsorNotifiedEntry(
            any(ProtocolDeviation.class), any(Instant.class), eq(true));
    }

    @Test
    void unknown_connector_id_writes_failed_ledger_entry_without_sending() {
        notifier.notify(request(PiApprovalStatus.ESCALATED, "dr-smith@v1", "unknown-connector"));

        assertThat(slackConnector.sent()).isEmpty();
        verify(ledgerWriter).writeSponsorNotifiedEntry(
            any(ProtocolDeviation.class), any(Instant.class), eq(false));
    }

    @Test
    void expired_notification_body_omits_null_pi_and_contains_deviation_type() {
        SponsorNotificationRequest req = new SponsorNotificationRequest(
            UUID.randomUUID(), siteId, deviationId,
            "CONSENT_DEVIATION", DeviationSeverity.MAJOR,
            PiApprovalStatus.EXPIRED, null,
            "slack", "https://hooks.slack.com/test"
        );

        notifier.notify(req);

        assertThat(slackConnector.sent()).hasSize(1);
        assertThat(slackConnector.sent().get(0).body())
            .contains("deadline expired")
            .contains("CONSENT_DEVIATION")
            .doesNotContain("null");
        verify(ledgerWriter).writeSponsorNotifiedEntry(
            any(ProtocolDeviation.class), any(Instant.class), eq(true));
    }

    @Test
    void connector_send_exception_writes_failed_entry_without_rethrowing() {
        slackConnector.setShouldThrow(true);

        notifier.notify(request(PiApprovalStatus.ESCALATED, "dr-smith@v1", "slack"));  // must not throw

        verify(ledgerWriter).writeSponsorNotifiedEntry(
            any(ProtocolDeviation.class), any(Instant.class), eq(false));
    }

    @Test
    void notification_title_reflects_actual_severity() {
        SponsorNotificationRequest req = new SponsorNotificationRequest(
            UUID.randomUUID(), siteId, deviationId,
            "PROTOCOL_PROCEDURE", DeviationSeverity.CRITICAL,
            PiApprovalStatus.ESCALATED, "dr-smith@v1",
            "slack", "https://hooks.slack.com/test"
        );

        notifier.notify(req);

        assertThat(slackConnector.sent()).hasSize(1);
        assertThat(slackConnector.sent().get(0).title())
            .startsWith("[CRITICAL Deviation]")
            .doesNotContain("[MAJOR");
    }

    private SponsorNotificationRequest request(PiApprovalStatus status, String piId, String connectorId) {
        return new SponsorNotificationRequest(
            UUID.randomUUID(), siteId, deviationId,
            "CONSENT_DEVIATION", DeviationSeverity.MAJOR,
            status, piId, connectorId, "https://hooks.slack.com/test"
        );
    }
}
