package io.casehub.clinical.service;

import io.casehub.clinical.api.IrbApprovalResolvedEvent;
import io.casehub.clinical.api.model.IrbDecision;
import io.casehub.clinical.entity.IrbApproval;
import io.casehub.clinical.memory.ClinicalMemoryService;
import io.casehub.work.api.WorkItemLifecycleEvent;
import io.casehub.work.api.WorkItem;
import io.casehub.work.api.WorkItemStatus;
import io.casehub.work.engine.PlanItemCallerRef;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@QuarkusTest
class IrbDecisionListenerTest {

    @Inject IrbDecisionListener listener;
    @InjectMock IrbApprovalLedgerWriter ledgerWriter;
    @InjectMock ClinicalDeviationCaseHub caseHub;
    @InjectMock Event<IrbApprovalResolvedEvent> resolvedEvents;
    @InjectMock ClinicalMemoryService memoryService;

    private UUID approvalId;
    private UUID deviationId;

    @BeforeEach
    void setUpMocks() {
        doNothing().when(memoryService).storeIrbDecision(any(), any(), any(), any(), any());
    }

    @BeforeEach
    @Transactional
    void setUp() {
        approvalId = UUID.randomUUID();
        deviationId = UUID.randomUUID();

        IrbApproval approval = new IrbApproval();
        approval.id = approvalId;
        approval.siteId = UUID.randomUUID();
        approval.deviationId = deviationId;
        approval.reviewType = "FULL";
        approval.committeeId = "irb-oncology";
        approval.decisionDeadline = Instant.now().plusSeconds(86400);
        // decision defaults to IrbDecision.PENDING from field initializer
        approval.persist();
    }

    // --- Helper factories ---

    private WorkItemLifecycleEvent completedEvent(String decisionJson) {
        WorkItem workItem = WorkItem.builder()
                .id(UUID.randomUUID())
                .status(WorkItemStatus.COMPLETED)
                .payload("{\"deviationId\":\"" + deviationId + "\"}")
                .resolution("{\"decision\":\"" + decisionJson + "\"}")
                .build();
        return WorkItemLifecycleEvent.of("COMPLETED", workItem, "irb-test", null);
    }

    private WorkItemLifecycleEvent expiredEvent() {
        UUID caseId = UUID.randomUUID();
        WorkItem workItem = WorkItem.builder()
                .id(UUID.randomUUID())
                .status(WorkItemStatus.EXPIRED)
                .payload("{\"deviationId\":\"" + deviationId + "\"}")
                .callerRef(PlanItemCallerRef.encode(caseId, "irb-consultation"))
                .build();
        return WorkItemLifecycleEvent.of("EXPIRED", workItem, "irb-test", null);
    }

    private WorkItemLifecycleEvent nonIrbEvent() {
        WorkItem workItem = WorkItem.builder()
                .id(UUID.randomUUID())
                .status(WorkItemStatus.COMPLETED)
                .payload("{}")
                .build();
        return WorkItemLifecycleEvent.of("COMPLETED", workItem, "irb-test", null);
    }

    // --- Tests ---

    @Test
    void approved_workitem_calls_writeDecisionEntry() {
        listener.onWorkItemLifecycle(completedEvent("APPROVED"));

        ArgumentCaptor<IrbApproval> captor = ArgumentCaptor.forClass(IrbApproval.class);
        verify(ledgerWriter).writeDecisionEntry(captor.capture());
        assertThat(captor.getValue().id).isEqualTo(approvalId);
        assertThat(captor.getValue().decision).isEqualTo(IrbDecision.APPROVED);
    }

    @Test
    void expired_workitem_signals_case_and_calls_writeDecisionEntry() {
        listener.onWorkItemLifecycle(expiredEvent());

        verify(caseHub).signal(any(UUID.class), eq("irbConsultation"), any());
        verify(ledgerWriter).writeDecisionEntry(any(IrbApproval.class));
    }

    @Test
    void non_irb_workitem_skipped() {
        listener.onWorkItemLifecycle(nonIrbEvent());

        verifyNoInteractions(ledgerWriter);
        verifyNoInteractions(caseHub);
    }

    @Test
    void writeDecisionEntry_throws_calls_writeObserverFailureEntry() {
        doThrow(new RuntimeException("ledger write failed"))
            .when(ledgerWriter).writeDecisionEntry(any());

        assertThatCode(() -> listener.onWorkItemLifecycle(completedEvent("APPROVED")))
            .doesNotThrowAnyException();

        ArgumentCaptor<IrbApproval> captor = ArgumentCaptor.forClass(IrbApproval.class);
        verify(ledgerWriter).writeObserverFailureEntry(captor.capture());
        assertThat(captor.getValue().id).isEqualTo(approvalId);
    }

    @Test
    void writeDecisionEntry_and_fallback_both_throw_does_not_propagate() {
        doThrow(new RuntimeException("ledger write failed"))
            .when(ledgerWriter).writeDecisionEntry(any());
        doThrow(new RuntimeException("fallback write failed"))
            .when(ledgerWriter).writeObserverFailureEntry(any());

        assertThatCode(() -> listener.onWorkItemLifecycle(completedEvent("APPROVED")))
            .doesNotThrowAnyException();
    }

    @Test
    void fireAsync_throws_after_ledger_written_no_failure_entry() {
        doThrow(new RuntimeException("fireAsync failed"))
            .when(resolvedEvents).fireAsync(any());

        assertThatCode(() -> listener.onWorkItemLifecycle(completedEvent("APPROVED")))
            .doesNotThrowAnyException();

        verify(ledgerWriter, never()).writeObserverFailureEntry(any(IrbApproval.class));
    }
}
