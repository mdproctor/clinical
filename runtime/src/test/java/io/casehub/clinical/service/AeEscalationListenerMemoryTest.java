package io.casehub.clinical.service;

import io.casehub.api.context.CaseContext;
import io.casehub.clinical.api.AeEscalationCompletedEvent;
import io.casehub.clinical.api.model.CtcaeGrade;
import io.casehub.clinical.memory.ClinicalMemoryService;
import io.casehub.engine.common.internal.model.CaseInstance;
import io.casehub.engine.common.spi.CaseInstanceRepository;
import io.casehub.engine.common.spi.event.CaseLifecycleEvent;
import io.smallrye.mutiny.Uni;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * @QuarkusTest integration test — verifies ClinicalMemoryService.storeAeOutcome()
 * is called after a successful AE escalation completion, and skipped when tenantId
 * is absent from the case context.
 */
@QuarkusTest
class AeEscalationListenerMemoryTest {

    @Inject AeEscalationListener listener;
    @InjectMock ClinicalMemoryService memoryService;
    @InjectMock CaseInstanceRepository caseInstanceRepository;
    @InjectMock AeEscalationLedgerWriter ledgerWriter;
    @InjectMock AeStatusUpdater statusUpdater;
    @InjectMock Event<AeEscalationCompletedEvent> completedEvents;

    @BeforeEach
    void stubDefaults() {
        // Prevent null-return side effects per GE-20260604-4298f9
        doNothing().when(memoryService).storeAeOutcome(any(), any(), any(), any(), anyBoolean(), any());
        when(completedEvents.fireAsync(any())).thenReturn(java.util.concurrent.CompletableFuture.completedFuture(null));
    }

    @Test
    void storeAeOutcome_called_with_correct_args_on_completion() {
        UUID caseId = UUID.randomUUID();
        UUID aeId = UUID.randomUUID();
        UUID enrollmentId = UUID.randomUUID();
        UUID siteId = UUID.randomUUID();

        CaseContext ctx = buildContext(aeId, enrollmentId, siteId, "GRADE_3",
            Map.of(AeEscalationListener.OUTCOME_KEY, "REVIEWED"), "completed", "test-tenant");

        CaseInstance instance = mock(CaseInstance.class);
        when(instance.getCaseContext()).thenReturn(ctx);
        when(caseInstanceRepository.findByUuid(eq(caseId), any()))
            .thenReturn(Uni.createFrom().item(instance));
        when(statusUpdater.markCompleted(aeId)).thenReturn(true);

        listener.onCaseLifecycle(goalReached(caseId));

        ArgumentCaptor<UUID> aeCaptor      = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<UUID> enrollCaptor  = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<String> tenantCaptor = ArgumentCaptor.forClass(String.class);

        verify(memoryService).storeAeOutcome(aeCaptor.capture(), enrollCaptor.capture(),
            any(), any(), anyBoolean(), tenantCaptor.capture());

        assertThat(aeCaptor.getValue()).isEqualTo(aeId);
        assertThat(enrollCaptor.getValue()).isEqualTo(enrollmentId);
        assertThat(tenantCaptor.getValue()).isEqualTo("test-tenant");
    }

    @Test
    void storeAeOutcome_skipped_when_tenantId_absent() {
        UUID caseId = UUID.randomUUID();
        UUID aeId = UUID.randomUUID();
        UUID enrollmentId = UUID.randomUUID();
        UUID siteId = UUID.randomUUID();

        // No tenantId in context
        CaseContext ctx = buildContext(aeId, enrollmentId, siteId, "GRADE_3",
            Map.of(AeEscalationListener.OUTCOME_KEY, "REVIEWED"), "completed", null);

        CaseInstance instance = mock(CaseInstance.class);
        when(instance.getCaseContext()).thenReturn(ctx);
        when(caseInstanceRepository.findByUuid(eq(caseId), any()))
            .thenReturn(Uni.createFrom().item(instance));
        when(statusUpdater.markCompleted(aeId)).thenReturn(true);

        listener.onCaseLifecycle(goalReached(caseId));

        verify(memoryService, never()).storeAeOutcome(any(), any(), any(), any(), anyBoolean(), any());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static CaseContext buildContext(UUID aeId, UUID enrollmentId, UUID siteId,
                                            String grade, Object safetyReview,
                                            Object dsmbEscalation, String tenantId) {
        CaseContext ctx = mock(CaseContext.class);
        when(ctx.getPath("aeId")).thenReturn(aeId.toString());
        when(ctx.getPath("enrollmentId")).thenReturn(enrollmentId.toString());
        when(ctx.getPath("siteId")).thenReturn(siteId.toString());
        when(ctx.getPath("grade")).thenReturn(grade);
        when(ctx.getPath("safetyReview")).thenReturn(safetyReview);
        when(ctx.getPath("dsmbEscalation")).thenReturn(dsmbEscalation);
        when(ctx.getPath("tenantId")).thenReturn(tenantId);
        return ctx;
    }

    private static CaseLifecycleEvent goalReached(UUID caseId) {
        return new CaseLifecycleEvent(
            caseId, null, "CompleteCase", "GoalReached", "RUNNING", "system", "system", null);
    }
}
