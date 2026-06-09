package io.casehub.clinical.service;

import io.casehub.clinical.api.AdverseEventReportedEvent;
import io.casehub.clinical.api.model.CtcaeGrade;
import io.casehub.clinical.api.model.SiteStatus;
import io.casehub.clinical.api.model.TrialPhase;
import io.casehub.clinical.api.model.TrialStatus;
import io.casehub.clinical.entity.ClinicalTrial;
import io.casehub.clinical.entity.TrialSite;
import io.casehub.clinical.ledger.SafetyOfficerNotificationLedgerEntry;
import io.casehub.ledger.runtime.repository.LedgerEntryRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end integration test for the safety officer adverse event notification chain.
 * Verifies that an AE reported event triggers connector delivery and ledger writes,
 * and that connector delivery failures (caught by DefaultSafetyOfficerNotifier's own catch)
 * produce a delivered=false ledger entry. Observer-level fallback (error before the notifier
 * is reached) is covered by SafetyOfficerNotificationListenerTest.
 *
 * <p>Design notes:
 * - Call listener.onAeReported(event) directly (synchronous). DefaultSafetyOfficerNotifier.notify()
 *   runs in @Transactional(REQUIRES_NEW) and completes before the listener returns.
 * - Entity setup: only ClinicalTrial + TrialSite needed. No AdverseEvent entity — the listener
 *   uses only event record fields (aeId, enrollmentId, siteId, grade).
 * - TestSlackConnector is @Singleton — call reset() in @BeforeEach since the bean persists across tests.
 * - Query ledger by calling findLatestBySubjectId(aeId) and casting to SafetyOfficerNotificationLedgerEntry.
 * - Test methods annotated @Transactional so Panache queries work. REQUIRES_NEW writes are committed.
 */
@QuarkusTest
class SafetyOfficerNotificationIntegrationTest {

    @Inject SafetyOfficerNotificationListener listener;
    @Inject TestSlackConnector slackConnector;
    @Inject LedgerEntryRepository ledgerEntryRepository;

    private UUID siteId;

    @BeforeEach
    @Transactional
    void setUp() {
        slackConnector.reset();

        UUID trialId = UUID.randomUUID();
        siteId = UUID.randomUUID();

        ClinicalTrial trial = new ClinicalTrial();
        trial.id = trialId;
        trial.protocolId = "ONCO-INT-001";
        trial.phase = TrialPhase.PHASE_III;
        trial.sponsor = "Integration Pharma";
        trial.targetEnrollment = 100;
        trial.status = TrialStatus.ACTIVE;
        trial.safetyOfficerConnectorId = "slack";
        trial.safetyOfficerDestination = "https://hooks.slack.com/safety-officer-integration-test";
        trial.persist();

        TrialSite site = new TrialSite();
        site.id = siteId;
        site.trialId = trialId;
        site.investigatorId = "dr-jones@v1";
        site.status = SiteStatus.ACTIVE;
        site.persist();
    }

    @Test
    @Transactional
    void grade3_ae_triggers_safety_officer_slack_notification() {
        UUID aeId = UUID.randomUUID();
        UUID enrollmentId = UUID.randomUUID();
        AdverseEventReportedEvent event = new AdverseEventReportedEvent(
            aeId, enrollmentId, siteId, CtcaeGrade.GRADE_3, Instant.now(), "test-tenant");

        listener.onAeReported(event);

        assertThat(slackConnector.sent()).hasSize(1);
        assertThat(slackConnector.sent().get(0).destination())
            .isEqualTo("https://hooks.slack.com/safety-officer-integration-test");
        assertThat(slackConnector.sent().get(0).body())
            .contains("Severe")  // CTCAE Grade 3 label
            .contains("24h");    // GCP SLA

        SafetyOfficerNotificationLedgerEntry entry =
            (SafetyOfficerNotificationLedgerEntry)
            ledgerEntryRepository.findLatestBySubjectId(aeId, "default").orElse(null);
        assertThat(entry).isNotNull();
        assertThat(entry.delivered).isTrue();
        assertThat(entry.aeId).isEqualTo(aeId);
        assertThat(entry.siteId).isEqualTo(siteId);
        assertThat(entry.connectorId).isEqualTo("slack");
        assertThat(entry.destination).isEqualTo("https://hooks.slack.com/safety-officer-integration-test");
    }

    @Test
    @Transactional
    void connector_delivery_failure_writes_failed_ledger_entry() {
        slackConnector.setShouldThrow(true);

        UUID aeId = UUID.randomUUID();
        UUID enrollmentId = UUID.randomUUID();
        AdverseEventReportedEvent event = new AdverseEventReportedEvent(
            aeId, enrollmentId, siteId, CtcaeGrade.GRADE_4, Instant.now(), "test-tenant");

        listener.onAeReported(event);

        assertThat(slackConnector.sent()).isEmpty();

        SafetyOfficerNotificationLedgerEntry entry =
            (SafetyOfficerNotificationLedgerEntry)
            ledgerEntryRepository.findLatestBySubjectId(aeId, "default").orElse(null);
        assertThat(entry).isNotNull();
        assertThat(entry.delivered).isFalse();
        assertThat(entry.connectorId).isEqualTo("slack");
    }
}
