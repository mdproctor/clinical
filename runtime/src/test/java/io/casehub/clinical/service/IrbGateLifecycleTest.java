package io.casehub.clinical.service;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.casehub.clinical.api.ProtocolDeviationResolvedEvent;
import io.casehub.clinical.api.model.DeviationSeverity;
import io.casehub.clinical.api.model.EscalationRequirement;
import io.casehub.clinical.api.model.IrbDecision;
import io.casehub.clinical.api.model.PiApprovalStatus;
import io.casehub.clinical.entity.IrbApproval;
import io.casehub.clinical.support.WorkItemCompletionCapture;
import io.casehub.clinical.support.WorkItemQueries;
import io.casehub.api.model.CaseStatus;
import io.casehub.engine.spi.CaseInstanceRepository;
import io.casehub.work.runtime.event.WorkItemLifecycleEvent;
import io.casehub.work.runtime.model.WorkItem;
import io.casehub.work.runtime.model.WorkItemStatus;
import io.casehub.work.runtime.service.WorkItemService;
import io.casehub.workadapter.WorkItemLifecycleAdapter;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class IrbGateLifecycleTest {

    @Inject IrbDeviationCaseService irbDeviationCaseService;
    @Inject IrbDecisionListener irbDecisionListener;
    @Inject WorkItemQueries workItemQueries;
    @Inject WorkItemService workItemService;
    @Inject CaseInstanceRepository caseInstanceRepository;
    @Inject WorkItemCompletionCapture completionCapture;
    @Inject WorkItemLifecycleAdapter lifecycleAdapter;

    private UUID deviationId;
    private UUID siteId;

    @BeforeEach
    void setup() {
        deviationId = UUID.randomUUID();
        siteId = UUID.randomUUID();
        completionCapture.reset();
    }

    @Test
    void irb_approved_full_lifecycle() throws Exception {
        // Checkpoint 1: start IRB case — call directly through CDI proxy (@Transactional honoured)
        irbDeviationCaseService.onDeviationResolved(criticalDeviationApproved());

        // Checkpoint 2: await IRB WorkItem (engine#312 — creation may be delayed)
        await().atMost(5, SECONDS).pollInterval(100, MILLISECONDS)
                .untilAsserted(() -> {
                    List<WorkItem> items = irbWorkItems();
                    assertThat(items).as("IRB WorkItem").isNotEmpty();
                    assertThat(items.get(0).title).contains("IRB consultation");
                });

        WorkItem irbWorkItem = irbWorkItems().get(0);

        // Checkpoint 3: complete WorkItem — fires async CDI lifecycle event to all observers
        String resolution = "{\"decision\":\"APPROVED\",\"committeeId\":\"irb-001\",\"decidedAt\":\"2026-05-22T12:00:00Z\"}";
        workItemService.completeFromSystem(irbWorkItem.id, "irb-committee", resolution);

        // Verify in-process CDI delivery to IrbDecisionListener (in-process — reliable)
        await().atMost(3, SECONDS).pollInterval(100, MILLISECONDS)
                .untilAsserted(() -> assertThat(completionCapture.wasCompleted(irbWorkItem.id)).isTrue());

        // Verify IrbDecisionListener updated the IrbApproval
        await().atMost(3, SECONDS).pollInterval(100, MILLISECONDS)
                .untilAsserted(() ->
                        assertThat(approvalDecision()).isEqualTo(IrbDecision.APPROVED));

        // Drive the engine path directly (engine#315 — WorkItemLifecycleAdapter @ObservesAsync
        // delivery to indexed external jar observers is unreliable in tests)
        WorkItem completed = irbWorkItems().get(0);
        lifecycleAdapter.onWorkItemLifecycle(
                WorkItemLifecycleEvent.of("COMPLETED", completed, "irb-committee", completed.resolution));

        // Checkpoint 4: case completes when outputMapping fires irbConsultation != null (goal: irb-decided)
        UUID caseId = caseIdFrom(completed.callerRef);
        await().atMost(5, SECONDS).pollInterval(100, MILLISECONDS)
                .untilAsserted(() -> {
                    var instance = caseInstanceRepository.findByUuid(caseId)
                            .await().atMost(Duration.ofSeconds(2));
                    assertThat(instance).isNotNull();
                    assertThat(instance.getState()).isEqualTo(CaseStatus.COMPLETED);
                });
    }

    @Test
    void irb_expired_updates_approval_and_completes_case() {
        irbDeviationCaseService.onDeviationResolved(criticalDeviationApproved());

        await().atMost(5, SECONDS).pollInterval(100, MILLISECONDS)
                .untilAsserted(() -> assertThat(irbWorkItems()).isNotEmpty());

        WorkItem irbWorkItem = irbWorkItems().get(0);

        // Simulate expiry via direct listener invocation:
        // IrbDecisionListener handles EXPIRED by updating IrbApproval AND signaling the case directly
        // (adapter would call markFaulted() which doesn't fire outputMapping — listener bypasses that).
        // WorkItemLifecycleEvent.of uses workItem.status for event.status() — must set EXPIRED on the
        // entity before constructing the event so IrbDecisionListener.resolveDecision returns EXPIRED.
        irbWorkItem.status = WorkItemStatus.EXPIRED;
        irbDecisionListener.onWorkItemLifecycle(
                WorkItemLifecycleEvent.of("EXPIRED", irbWorkItem, "system", null));

        await().atMost(3, SECONDS).pollInterval(100, MILLISECONDS)
                .untilAsserted(() ->
                        assertThat(approvalDecision()).isEqualTo(IrbDecision.EXPIRED));

        UUID caseId = caseIdFrom(irbWorkItem.callerRef);
        await().atMost(5, SECONDS).pollInterval(100, MILLISECONDS)
                .untilAsserted(() -> {
                    var instance = caseInstanceRepository.findByUuid(caseId)
                            .await().atMost(Duration.ofSeconds(2));
                    assertThat(instance).isNotNull();
                    assertThat(instance.getState()).isEqualTo(CaseStatus.COMPLETED);
                });
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private ProtocolDeviationResolvedEvent criticalDeviationApproved() {
        return new ProtocolDeviationResolvedEvent(
                deviationId, siteId, DeviationSeverity.CRITICAL,
                EscalationRequirement.IRB_REVIEW, PiApprovalStatus.APPROVED,
                "CONSENT_DEVIATION", "pi-001");
    }

    private List<WorkItem> irbWorkItems() {
        return workItemQueries.scanAll().stream()
                .filter(wi -> wi.payload != null && wi.payload.contains(deviationId.toString()))
                .toList();
    }

    @Transactional
    IrbDecision approvalDecision() {
        IrbApproval approval = IrbApproval.find("deviationId = ?1", deviationId).firstResult();
        return approval != null ? approval.decision : null;
    }

    private UUID caseIdFrom(String callerRef) {
        var ref = io.casehub.workadapter.CallerRef.parse(callerRef);
        return ref != null ? ref.caseId() : null;
    }
}
