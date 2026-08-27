package io.casehub.clinical.service;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.casehub.clinical.api.AdverseEventReportedEvent;
import io.casehub.clinical.api.model.AeOutcome;
import io.casehub.clinical.api.model.CtcaeGrade;
import io.casehub.clinical.api.model.EventActuality;
import io.casehub.clinical.entity.AdverseEvent;
import io.casehub.clinical.support.WorkItemQueries;
import io.casehub.platform.testing.FixedCurrentPrincipal;
import io.casehub.work.api.WorkItemStatus;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * End-to-end invariant: WorkItem.expiresAt == ae.reportedAt + indReportingWindow(grade).
 *
 * <p>Verifies the full data path:
 * <ol>
 *   <li>ae.reportedAt + window → indReportingDeadline (RegulatorySubmissionCaseService context map)
 *   <li>indReportingDeadline in case context → .indReportingDeadline JQ (expiresAtExpression in
 *       regulatory-submission.yaml)
 *   <li>JQ resolution → HumanTaskScheduleEvent.expiresAtDeadline (CaseContextChangedEventHandler)
 *   <li>expiresAtDeadline → WorkItem.expiresAt (HumanTaskScheduleHandler)
 * </ol>
 *
 * <p>Full engine on classpath. No {@code @InjectMock} on
 * {@code ClinicalRegulatorySubmissionCaseHub} — the real case must run to exercise the JQ
 * expression path. Awaitility required — {@code HumanTaskScheduleHandler} fires on a Vert.x
 * worker thread. {@code ae.tenantId = principal.tenancyId()} required — omission causes
 * {@code SecurityException} from {@code MemoryPermissions.assertTenant()}.
 *
 * <p>WorkItem filter uses {@code callerRef.contains("case:" + caseId)} to isolate each test's
 * WorkItem — prevents cross-test interference when both tests run in the same JVM (the
 * in-memory WorkItemStore is shared across tests).
 */
@QuarkusTest
class RegulatorySubmissionDeadlineLifecycleTest {

    @Inject RegulatorySubmissionCaseService service;
    @Inject WorkItemQueries workItemQueries;
    @Inject FixedCurrentPrincipal principal;

    @Test
    void grade3_workItem_expiresAt_equals_reportedAt_plus_15_days() {
        Instant reportedAt = Instant.parse("2026-07-01T10:00:00Z");
        UUID aeId = persistAe(CtcaeGrade.GRADE_3, reportedAt);
        AdverseEventReportedEvent event = buildEvent(aeId, CtcaeGrade.GRADE_3, reportedAt);

        service.onAdverseEventReported(event);

        // Phase 3 persists the engine caseId — wait for it so we can use it to filter WorkItems
        await().atMost(10, SECONDS).pollInterval(100, MILLISECONDS)
                .untilAsserted(() -> assertThat(findAe(aeId).regulatorySubmissionCaseId).isNotNull());
        UUID caseId = findAe(aeId).regulatorySubmissionCaseId;

        Instant expectedExpiry = Instant.parse("2026-07-16T10:00:00Z"); // reportedAt + 15 days
        await().atMost(10, SECONDS).pollInterval(100, MILLISECONDS).untilAsserted(() -> {
            var workItem = workItemQueries.scanAll().stream()
                    .filter(wi -> wi.candidateGroups() != null
                            && wi.candidateGroups().contains("regulatory-affairs")
                            && wi.callerRef() != null
                            && wi.callerRef().contains("case:" + caseId))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(
                            "No regulatory-affairs WorkItem found yet for case " + caseId));
            assertThat(workItem.expiresAt())
                    .as("WorkItem.expiresAt must equal reportedAt + 15 days (21 CFR 312.32(c)(1)(ii))")
                    .isEqualTo(expectedExpiry);
            assertThat(workItem.status()).isEqualTo(WorkItemStatus.PENDING);
        });
    }

    @Test
    void grade4_workItem_expiresAt_equals_reportedAt_plus_7_days() {
        Instant reportedAt = Instant.parse("2026-07-02T10:00:00Z");
        UUID aeId = persistAe(CtcaeGrade.GRADE_4, reportedAt);
        AdverseEventReportedEvent event = buildEvent(aeId, CtcaeGrade.GRADE_4, reportedAt);

        service.onAdverseEventReported(event);

        // Phase 3 persists the engine caseId — wait for it so we can use it to filter WorkItems
        await().atMost(10, SECONDS).pollInterval(100, MILLISECONDS)
                .untilAsserted(() -> assertThat(findAe(aeId).regulatorySubmissionCaseId).isNotNull());
        UUID caseId = findAe(aeId).regulatorySubmissionCaseId;

        Instant expectedExpiry = Instant.parse("2026-07-09T10:00:00Z"); // reportedAt + 7 days
        await().atMost(10, SECONDS).pollInterval(100, MILLISECONDS).untilAsserted(() -> {
            var workItem = workItemQueries.scanAll().stream()
                    .filter(wi -> wi.candidateGroups() != null
                            && wi.candidateGroups().contains("regulatory-affairs")
                            && wi.callerRef() != null
                            && wi.callerRef().contains("case:" + caseId))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(
                            "No regulatory-affairs WorkItem found yet for case " + caseId));
            assertThat(workItem.expiresAt())
                    .as("WorkItem.expiresAt must equal reportedAt + 7 days (21 CFR 312.32(c)(1)(i))")
                    .isEqualTo(expectedExpiry);
            assertThat(workItem.status()).isEqualTo(WorkItemStatus.PENDING);
        });
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    @Transactional
    UUID persistAe(CtcaeGrade grade, Instant reportedAt) {
        AdverseEvent ae = new AdverseEvent();
        ae.id = UUID.randomUUID();
        ae.enrollmentId = UUID.randomUUID();
        ae.grade = grade;
        ae.unexpected = true;
        ae.suspected = true;
        ae.actuality = EventActuality.ACTUAL;
        ae.outcome = AeOutcome.ONGOING;
        ae.occurredAt = reportedAt;
        ae.reportedAt = reportedAt;
        // Required: prevents SecurityException from MemoryPermissions.assertTenant()
        ae.tenantId = principal.tenancyId();
        ae.persist();
        return ae.id;
    }

    @Transactional
    AdverseEvent findAe(UUID aeId) {
        return AdverseEvent.findById(aeId);
    }

    private AdverseEventReportedEvent buildEvent(UUID aeId, CtcaeGrade grade, Instant reportedAt) {
        return new AdverseEventReportedEvent(
                aeId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                grade,
                reportedAt,
                principal.tenancyId());
    }
}
