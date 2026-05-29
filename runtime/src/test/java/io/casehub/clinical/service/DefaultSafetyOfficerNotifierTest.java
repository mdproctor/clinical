package io.casehub.clinical.service;

import io.casehub.clinical.api.SafetyOfficerNotificationRequest;
import io.casehub.clinical.api.model.CtcaeGrade;
import io.casehub.connectors.Connector;
import io.casehub.connectors.ConnectorMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultSafetyOfficerNotifierTest {

    @Mock SafetyOfficerNotificationLedgerWriter ledgerWriter;

    private FakeConnector slackConnector;
    private DefaultSafetyOfficerNotifier notifier;

    @BeforeEach
    void setUp() {
        slackConnector = new FakeConnector("slack");
        notifier = new DefaultSafetyOfficerNotifier(List.of(slackConnector), ledgerWriter);
    }

    @Test
    void grade5_title_has_critical_prefix_and_writes_delivered_ledger_entry() {
        notifier.notify(request(CtcaeGrade.GRADE_5));

        assertThat(slackConnector.lastTitle()).startsWith("[CRITICAL]");
        assertThat(slackConnector.lastTitle()).contains("Death");
        verify(ledgerWriter).writeEntry(any(), any(), any(),
            eq(CtcaeGrade.GRADE_5), eq("slack"), any(), eq(true));
    }

    @Test
    void grade3_title_has_no_critical_prefix_and_contains_grade_label() {
        notifier.notify(request(CtcaeGrade.GRADE_3));

        assertThat(slackConnector.lastTitle()).doesNotContain("[CRITICAL]");
        assertThat(slackConnector.lastTitle()).contains("Severe");
        verify(ledgerWriter).writeEntry(any(), any(), any(),
            eq(CtcaeGrade.GRADE_3), eq("slack"), any(), eq(true));
    }

    @Test
    void grade4_title_contains_grade_label() {
        notifier.notify(request(CtcaeGrade.GRADE_4));

        assertThat(slackConnector.lastTitle()).contains("Life-threatening");
        verify(ledgerWriter).writeEntry(any(), any(), any(),
            eq(CtcaeGrade.GRADE_4), eq("slack"), any(), eq(true));
    }

    @Test
    void unknown_connector_writes_failed_ledger_entry_without_sending() {
        SafetyOfficerNotificationRequest req = new SafetyOfficerNotificationRequest(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            CtcaeGrade.GRADE_4, "unknown-connector", "https://example.com");

        notifier.notify(req);

        assertThat(slackConnector.sent()).isEmpty();
        verify(ledgerWriter).writeEntry(any(), any(), any(),
            eq(CtcaeGrade.GRADE_4), eq("unknown-connector"), any(), eq(false));
    }

    @Test
    void connector_send_exception_writes_failed_entry_without_rethrowing() {
        slackConnector.setShouldThrow(true);

        assertThatNoException().isThrownBy(() -> notifier.notify(request(CtcaeGrade.GRADE_5)));

        verify(ledgerWriter).writeEntry(any(), any(), any(),
            eq(CtcaeGrade.GRADE_5), eq("slack"), any(), eq(false));
    }

    @Test
    void notification_body_contains_ae_id_and_enrollment_id() {
        UUID aeId = UUID.randomUUID();
        UUID enrollmentId = UUID.randomUUID();
        SafetyOfficerNotificationRequest req = new SafetyOfficerNotificationRequest(
            aeId, enrollmentId, UUID.randomUUID(), CtcaeGrade.GRADE_3,
            "slack", "https://hooks.slack.com/test");

        notifier.notify(req);

        assertThat(slackConnector.lastBody())
            .contains(aeId.toString())
            .contains(enrollmentId.toString());
    }

    private SafetyOfficerNotificationRequest request(final CtcaeGrade grade) {
        return new SafetyOfficerNotificationRequest(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            grade, "slack", "https://hooks.slack.com/test");
    }

    private static class FakeConnector implements Connector {
        private final String id;
        private final java.util.List<ConnectorMessage> sent = new java.util.ArrayList<>();
        private boolean shouldThrow = false;

        FakeConnector(String id) { this.id = id; }

        @Override public String id() { return id; }

        @Override
        public void send(ConnectorMessage message) {
            if (shouldThrow) throw new RuntimeException("connector failure");
            sent.add(message);
        }

        List<ConnectorMessage> sent() { return sent; }
        String lastTitle() { return sent.isEmpty() ? null : sent.get(sent.size() - 1).title(); }
        String lastBody() { return sent.isEmpty() ? null : sent.get(sent.size() - 1).body(); }
        void setShouldThrow(boolean v) { this.shouldThrow = v; }
    }
}
