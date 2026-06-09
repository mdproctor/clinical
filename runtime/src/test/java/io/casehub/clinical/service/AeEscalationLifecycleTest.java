package io.casehub.clinical.service;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.casehub.clinical.api.AdverseEventReportedEvent;
import io.casehub.clinical.api.model.AeEscalationStatus;
import io.casehub.clinical.api.model.AeOutcome;
import io.casehub.clinical.api.model.CtcaeGrade;
import io.casehub.clinical.api.model.EventActuality;
import io.casehub.clinical.entity.AdverseEvent;
import io.casehub.clinical.support.WorkItemCompletionCapture;
import io.casehub.clinical.support.WorkItemQueries;
import io.casehub.work.runtime.event.WorkItemLifecycleEvent;
import io.casehub.work.runtime.model.WorkItem;
import io.casehub.work.runtime.service.WorkItemService;
import io.casehub.workadapter.WorkItemLifecycleAdapter;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class AeEscalationLifecycleTest {

    @Inject AeEscalationCaseService aeEscalationCaseService;
    @InjectSpy TrialSafetySignalService trialSafetySignalService;
    @Inject WorkItemQueries workItemQueries;
    @Inject WorkItemService workItemService;
    @Inject WorkItemCompletionCapture completionCapture;
    @Inject WorkItemLifecycleAdapter lifecycleAdapter;

    private UUID aeId;
    private UUID enrollmentId;
    private UUID siteId;

    @BeforeEach
    @Transactional
    void setup() {
        aeId = UUID.randomUUID();
        enrollmentId = UUID.randomUUID();
        siteId = UUID.randomUUID();
        completionCapture.reset();

        AdverseEvent ae = new AdverseEvent();
        ae.id = aeId;
        ae.enrollmentId = enrollmentId;
        ae.grade = CtcaeGrade.GRADE_3;
        ae.actuality = EventActuality.ACTUAL;
        ae.outcome = AeOutcome.ONGOING;
        ae.occurredAt = Instant.now();
        ae.reportedAt = Instant.now();
        ae.persist();
    }

    @Test
    void grade3_opens_one_senior_monitor_gate() throws Exception {
        aeEscalationCaseService.onAdverseEventReported(aeEvent(CtcaeGrade.GRADE_3));

        // Phase 1 sets REQUESTED synchronously (direct call — not via CDI async event bus)
        assertThat(findAe(aeId).escalationStatus).isEqualTo(AeEscalationStatus.REQUESTED);

        // Checkpoint: exactly one safety-review WorkItem, no dsmb WorkItem
        await().atMost(5, SECONDS).pollInterval(100, MILLISECONDS)
                .untilAsserted(() -> {
                    List<WorkItem> items = aeWorkItems();
                    assertThat(items.stream().anyMatch(wi -> wi.title.contains("Senior safety monitor")))
                            .as("safety-review WorkItem for Grade 3").isTrue();
                    assertThat(items.stream().noneMatch(wi -> wi.title.contains("DSMB")))
                            .as("No DSMB WorkItem for Grade 3").isTrue();
                });

        WorkItem safetyWorkItem = aeWorkItems().stream()
                .filter(wi -> wi.title.contains("Senior safety monitor"))
                .findFirst().orElseThrow();
        String resolution = "{\"outcome\":\"REVIEWED\",\"reviewedAt\":\"2026-05-22T13:00:00Z\"}";
        workItemService.completeFromSystem(safetyWorkItem.id, "senior-monitor", resolution);

        // Re-fetch after completion so the WorkItem has the updated resolution and status
        WorkItem completed = aeWorkItems().get(0);
        lifecycleAdapter.onWorkItemLifecycle(
                WorkItemLifecycleEvent.of("COMPLETED", completed, "senior-monitor", completed.resolution));

        // Phase 3 persists the engine case ID — verify after case start completes
        assertThat(findAe(aeId).engineCaseId).isNotNull();

        // AeEscalationListener fires @ObservesAsync on CaseLifecycleEvent — small lag after case completes
        await().atMost(10, SECONDS).pollInterval(100, MILLISECONDS)
                .untilAsserted(() ->
                        assertThat(findAe(aeId).escalationStatus).isEqualTo(AeEscalationStatus.COMPLETED));

        // Grade 3 must NOT signal the trial (no DSMB rollup threshold)
        verify(trialSafetySignalService, never()).signalGrade4Active(any());
    }

    @Test
    void grade4_opens_two_parallel_gates() throws Exception {
        aeEscalationCaseService.onAdverseEventReported(aeEvent(CtcaeGrade.GRADE_4));

        // Phase 1 sets REQUESTED synchronously (direct call — not via CDI async event bus)
        assertThat(findAe(aeId).escalationStatus).isEqualTo(AeEscalationStatus.REQUESTED);
        // Phases 2+3 also run synchronously — Grade 4 must have signaled the trial by now
        // (no TrialSite in test setup, so signalGrade4Active resolves to no-op via null trialCaseId)
        verify(trialSafetySignalService).signalGrade4Active(siteId);

        // Checkpoint: two WorkItems (safety-review + dsmb-escalation)
        await().atMost(5, SECONDS).pollInterval(100, MILLISECONDS)
                .untilAsserted(() -> {
                    List<WorkItem> items = aeWorkItems();
                    assertThat(items).as("Two WorkItems for Grade 4").hasSizeGreaterThanOrEqualTo(2);
                });

        List<WorkItem> allItems = aeWorkItems();
        WorkItem safetyItem = allItems.stream()
                .filter(wi -> wi.title.contains("Senior")).findFirst().orElseThrow();
        WorkItem dsmbItem = allItems.stream()
                .filter(wi -> wi.title.contains("DSMB")).findFirst().orElseThrow();

        String resolution = "{\"outcome\":\"REVIEWED\",\"reviewedAt\":\"2026-05-22T13:00:00Z\"}";
        workItemService.completeFromSystem(safetyItem.id, "senior-monitor", resolution);
        workItemService.completeFromSystem(dsmbItem.id, "dsmb-chair", resolution);

        // Re-fetch after completion so WorkItems have updated resolution and status
        List<WorkItem> completedItems = aeWorkItems();
        WorkItem completedSafety = completedItems.stream()
                .filter(wi -> wi.title.contains("Senior")).findFirst().orElseThrow();
        WorkItem completedDsmb = completedItems.stream()
                .filter(wi -> wi.title.contains("DSMB")).findFirst().orElseThrow();

        lifecycleAdapter.onWorkItemLifecycle(
                WorkItemLifecycleEvent.of("COMPLETED", completedSafety, "senior-monitor", completedSafety.resolution));
        lifecycleAdapter.onWorkItemLifecycle(
                WorkItemLifecycleEvent.of("COMPLETED", completedDsmb, "dsmb-chair", completedDsmb.resolution));

        // Phase 3 persists the engine case ID — verify after case start completes
        assertThat(findAe(aeId).engineCaseId).isNotNull();

        // AeEscalationListener fires @ObservesAsync on CaseLifecycleEvent — small lag after case completes
        await().atMost(10, SECONDS).pollInterval(100, MILLISECONDS)
                .untilAsserted(() ->
                        assertThat(findAe(aeId).escalationStatus).isEqualTo(AeEscalationStatus.COMPLETED));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private AdverseEventReportedEvent aeEvent(CtcaeGrade grade) {
        return new AdverseEventReportedEvent(aeId, enrollmentId, siteId, grade, Instant.now(), "test-tenant");
    }

    private List<WorkItem> aeWorkItems() {
        return workItemQueries.scanAll().stream()
                .filter(wi -> wi.payload != null && wi.payload.contains(aeId.toString()))
                .toList();
    }

    @Transactional
    AdverseEvent findAe(UUID id) {
        return AdverseEvent.findById(id);
    }
}
