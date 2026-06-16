package io.casehub.clinical.service;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.casehub.clinical.api.AdverseEventReportedEvent;
import io.casehub.clinical.api.model.AeOutcome;
import io.casehub.clinical.api.model.CtcaeGrade;
import io.casehub.clinical.api.model.EventActuality;
import io.casehub.clinical.api.model.RegulatorySubmissionStatus;
import io.casehub.clinical.entity.AdverseEvent;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

@QuarkusTest
class RegulatorySubmissionCaseServiceTest {

    @Inject RegulatorySubmissionCaseService service;
    @InjectMock ClinicalRegulatorySubmissionCaseHub regulatorySubmissionCaseHub;

    @BeforeEach
    void stubCaseHub() {
        // Default: startCase() succeeds with a generated UUID — overridden in rollback test
        when(regulatorySubmissionCaseHub.startCase(any()))
                .thenReturn(CompletableFuture.completedFuture(UUID.randomUUID()));
    }

    @Test
    void grade5_unexpected_starts_regulatory_case() {
        UUID aeId = persistAe(CtcaeGrade.GRADE_5, true);
        AdverseEventReportedEvent event = buildEvent(aeId, CtcaeGrade.GRADE_5);

        service.onAdverseEventReported(event);

        await().atMost(10, SECONDS).pollInterval(100, MILLISECONDS).untilAsserted(() -> {
            AdverseEvent ae = findAe(aeId);
            assertThat(ae.regulatorySubmissionStatus).isEqualTo(RegulatorySubmissionStatus.PENDING);
            assertThat(ae.regulatorySubmissionCaseId).isNotNull();
        });
    }

    @Test
    void grade4_unexpected_does_not_start_regulatory_case() {
        UUID aeId = persistAe(CtcaeGrade.GRADE_4, true);
        AdverseEventReportedEvent event = buildEvent(aeId, CtcaeGrade.GRADE_4);

        service.onAdverseEventReported(event);

        AdverseEvent ae = findAe(aeId);
        assertThat(ae.regulatorySubmissionStatus).isEqualTo(RegulatorySubmissionStatus.NONE);
        assertThat(ae.regulatorySubmissionCaseId).isNull();
    }

    @Test
    void grade5_expected_does_not_start_regulatory_case() {
        UUID aeId = persistAe(CtcaeGrade.GRADE_5, false);
        AdverseEventReportedEvent event = buildEvent(aeId, CtcaeGrade.GRADE_5);

        service.onAdverseEventReported(event);

        AdverseEvent ae = findAe(aeId);
        assertThat(ae.regulatorySubmissionStatus).isEqualTo(RegulatorySubmissionStatus.NONE);
    }

    @Test
    void idempotency_guard_prevents_double_start() {
        UUID aeId = persistAe(CtcaeGrade.GRADE_5, true);
        setStatus(aeId, RegulatorySubmissionStatus.PENDING);
        AdverseEventReportedEvent event = buildEvent(aeId, CtcaeGrade.GRADE_5);

        service.onAdverseEventReported(event);

        assertThat(findAe(aeId).regulatorySubmissionCaseId).isNull();
    }

    @Test
    void start_case_failure_resets_status_to_none() {
        UUID aeId = persistAe(CtcaeGrade.GRADE_5, true);
        AdverseEventReportedEvent event = buildEvent(aeId, CtcaeGrade.GRADE_5);

        // Cause startCase() to throw
        when(regulatorySubmissionCaseHub.startCase(any()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("engine unavailable")));

        service.onAdverseEventReported(event);

        // Status resets to NONE so retry is possible
        assertThat(findAe(aeId).regulatorySubmissionStatus).isEqualTo(RegulatorySubmissionStatus.NONE);
        assertThat(findAe(aeId).regulatorySubmissionCaseId).isNull();
    }

    @Test
    void grade3_unexpected_starts_regulatory_case() {
        UUID aeId = persistAe(CtcaeGrade.GRADE_3, true);
        AdverseEventReportedEvent event = buildEvent(aeId, CtcaeGrade.GRADE_3);

        service.onAdverseEventReported(event);

        await().atMost(10, SECONDS).pollInterval(100, MILLISECONDS).untilAsserted(() -> {
            AdverseEvent ae = findAe(aeId);
            assertThat(ae.regulatorySubmissionStatus).isEqualTo(RegulatorySubmissionStatus.PENDING);
            assertThat(ae.regulatorySubmissionCaseId).isNotNull();
        });
    }

    @Test
    void grade3_expected_does_not_start_regulatory_case() {
        UUID aeId = persistAe(CtcaeGrade.GRADE_3, false);
        AdverseEventReportedEvent event = buildEvent(aeId, CtcaeGrade.GRADE_3);

        service.onAdverseEventReported(event);

        AdverseEvent ae = findAe(aeId);
        assertThat(ae.regulatorySubmissionStatus).isEqualTo(RegulatorySubmissionStatus.NONE);
        assertThat(ae.regulatorySubmissionCaseId).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void grade3_case_context_includes_15_day_ind_deadline() {
        final Instant fixedReportedAt = Instant.parse("2026-06-16T09:00:00Z");
        UUID aeId = persistAe(CtcaeGrade.GRADE_3, true, fixedReportedAt);
        AdverseEventReportedEvent event = buildEvent(aeId, CtcaeGrade.GRADE_3);

        service.onAdverseEventReported(event);

        await().atMost(10, SECONDS).pollInterval(100, MILLISECONDS).untilAsserted(() -> {
            ArgumentCaptor<Map> captor = ArgumentCaptor.forClass(Map.class);
            verify(regulatorySubmissionCaseHub).startCase(captor.capture());
            Map<String, Object> ctx = captor.getValue();
            assertThat(ctx).containsKey("indReportingDeadline");
            assertThat(Instant.parse((String) ctx.get("indReportingDeadline")))
                    .isEqualTo(fixedReportedAt.plus(Duration.ofDays(15)));
        });
    }

    @Test
    @SuppressWarnings("unchecked")
    void grade5_case_context_includes_7_day_ind_deadline() {
        final Instant fixedReportedAt = Instant.parse("2026-06-16T09:00:00Z");
        UUID aeId = persistAe(CtcaeGrade.GRADE_5, true, fixedReportedAt);
        AdverseEventReportedEvent event = buildEvent(aeId, CtcaeGrade.GRADE_5);

        service.onAdverseEventReported(event);

        await().atMost(10, SECONDS).pollInterval(100, MILLISECONDS).untilAsserted(() -> {
            ArgumentCaptor<Map> captor = ArgumentCaptor.forClass(Map.class);
            verify(regulatorySubmissionCaseHub).startCase(captor.capture());
            Map<String, Object> ctx = captor.getValue();
            assertThat(ctx).containsKey("indReportingDeadline");
            assertThat(Instant.parse((String) ctx.get("indReportingDeadline")))
                    .isEqualTo(fixedReportedAt.plus(Duration.ofDays(7)));
        });
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    @Transactional
    UUID persistAe(CtcaeGrade grade, boolean unexpected) {
        AdverseEvent ae = new AdverseEvent();
        ae.id = UUID.randomUUID();
        ae.enrollmentId = UUID.randomUUID();
        ae.grade = grade;
        ae.unexpected = unexpected;
        ae.suspected = true;
        ae.actuality = EventActuality.ACTUAL;
        ae.outcome = AeOutcome.ONGOING;
        ae.occurredAt = Instant.now();
        ae.reportedAt = Instant.now();
        ae.tenantId = "test-tenant";
        ae.persist();
        return ae.id;
    }

    @Transactional
    UUID persistAe(CtcaeGrade grade, boolean unexpected, Instant reportedAt) {
        AdverseEvent ae = new AdverseEvent();
        ae.id = UUID.randomUUID();
        ae.enrollmentId = UUID.randomUUID();
        ae.grade = grade;
        ae.unexpected = unexpected;
        ae.suspected = true;
        ae.actuality = EventActuality.ACTUAL;
        ae.outcome = AeOutcome.ONGOING;
        ae.occurredAt = Instant.now();
        ae.reportedAt = reportedAt;
        ae.tenantId = "test-tenant";
        ae.persist();
        return ae.id;
    }

    @Transactional
    void setStatus(UUID aeId, RegulatorySubmissionStatus status) {
        AdverseEvent ae = AdverseEvent.findById(aeId);
        ae.regulatorySubmissionStatus = status;
    }

    AdverseEventReportedEvent buildEvent(UUID aeId, CtcaeGrade grade) {
        return new AdverseEventReportedEvent(
                aeId, UUID.randomUUID(), UUID.randomUUID(), grade, Instant.now(), "test-tenant");
    }

    @Transactional
    AdverseEvent findAe(UUID aeId) {
        return AdverseEvent.findById(aeId);
    }
}
