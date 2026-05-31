package io.casehub.clinical.service;

import io.casehub.api.context.CaseContext;
import io.casehub.clinical.api.AeEscalationCompletedEvent;
import io.casehub.clinical.api.model.CtcaeGrade;
import io.casehub.engine.common.spi.event.CaseLifecycleEvent;
import io.casehub.engine.common.internal.model.CaseInstance;
import io.casehub.engine.common.spi.CaseInstanceRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.event.Event;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AeEscalationListenerTest {

    @Mock CaseInstanceRepository caseInstanceRepository;
    @Mock AeEscalationLedgerWriter ledgerWriter;
    @Mock AeStatusUpdater statusUpdater;
    @Mock Event<AeEscalationCompletedEvent> completedEvents;
    @InjectMocks AeEscalationListener listener;

    @Test
    void completed_event_carries_siteId_from_case_context() {
        UUID caseId = UUID.randomUUID();
        UUID aeId = UUID.randomUUID();
        UUID enrollmentId = UUID.randomUUID();
        UUID siteId = UUID.randomUUID();

        CaseContext ctx = mock(CaseContext.class);
        when(ctx.getPath("aeId")).thenReturn(aeId.toString());
        when(ctx.getPath("enrollmentId")).thenReturn(enrollmentId.toString());
        when(ctx.getPath("grade")).thenReturn("GRADE_4");
        when(ctx.getPath("siteId")).thenReturn(siteId.toString());
        when(ctx.getPath("safetyReview")).thenReturn(Map.of(AeEscalationListener.OUTCOME_KEY, "REVIEWED"));
        when(ctx.getPath("dsmbEscalation")).thenReturn("completed");

        CaseInstance instance = mock(CaseInstance.class);
        when(instance.getCaseContext()).thenReturn(ctx);
        when(caseInstanceRepository.findByUuid(caseId)).thenReturn(Uni.createFrom().item(instance));
        when(statusUpdater.markCompleted(aeId)).thenReturn(true);
        when(completedEvents.fireAsync(any())).thenReturn(CompletableFuture.completedFuture(null));

        listener.onCaseLifecycle(new CaseLifecycleEvent(
                caseId, "CompleteCase", "CaseCompleted", "COMPLETED", "system", "system", null));

        ArgumentCaptor<AeEscalationCompletedEvent> captor =
                ArgumentCaptor.forClass(AeEscalationCompletedEvent.class);
        verify(completedEvents).fireAsync(captor.capture());

        AeEscalationCompletedEvent fired = captor.getValue();
        assertThat(fired.aeId()).isEqualTo(aeId);
        assertThat(fired.grade()).isEqualTo(CtcaeGrade.GRADE_4);
        assertThat(fired.siteId()).isEqualTo(siteId);
    }

    @Test
    void non_completed_events_are_ignored() {
        listener.onCaseLifecycle(new CaseLifecycleEvent(
                UUID.randomUUID(), "StartCase", "CaseStarted", "RUNNING", "system", "system", null));

        verifyNoInteractions(caseInstanceRepository);
        verifyNoInteractions(completedEvents);
    }

    @Test
    void idempotency_guard_skips_ledger_write_on_duplicate_goal_reached() {
        UUID caseId = UUID.randomUUID();
        UUID aeId = UUID.randomUUID();

        CaseContext ctx = mock(CaseContext.class);
        when(ctx.getPath("aeId")).thenReturn(aeId.toString());
        when(statusUpdater.markCompleted(aeId)).thenReturn(false); // already COMPLETED

        CaseInstance instance = mock(CaseInstance.class);
        when(instance.getCaseContext()).thenReturn(ctx);
        when(caseInstanceRepository.findByUuid(caseId)).thenReturn(Uni.createFrom().item(instance));

        listener.onCaseLifecycle(new CaseLifecycleEvent(
                caseId, "CompleteCase", "GoalReached", "RUNNING", "system", "system", null));

        verifyNoInteractions(ledgerWriter);
        verifyNoInteractions(completedEvents);
    }

    // --- helper methods ---

    private CaseLifecycleEvent goalReachedEvent(UUID caseId) {
        return new CaseLifecycleEvent(
                caseId, "CompleteCase", "GoalReached", "RUNNING", "system", "system", null);
    }

    private void mockInstanceWith(UUID caseId, UUID aeId, UUID enrollmentId, String grade) {
        CaseContext ctx = mock(CaseContext.class);
        when(ctx.getPath("aeId")).thenReturn(aeId.toString());
        when(ctx.getPath("enrollmentId")).thenReturn(enrollmentId.toString());
        when(ctx.getPath("grade")).thenReturn(grade);
        when(ctx.getPath("siteId")).thenReturn(null);
        when(ctx.getPath("safetyReview")).thenReturn(null);
        when(ctx.getPath("dsmbEscalation")).thenReturn(null);
        CaseInstance instance = mock(CaseInstance.class);
        when(instance.getCaseContext()).thenReturn(ctx);
        when(caseInstanceRepository.findByUuid(caseId)).thenReturn(Uni.createFrom().item(instance));
    }

    @Test
    void writeCompletionEntry_throws_writes_observer_failure_entry() {
        UUID caseId = UUID.randomUUID();
        UUID aeId = UUID.randomUUID();
        UUID enrollmentId = UUID.randomUUID();
        mockInstanceWith(caseId, aeId, enrollmentId, "GRADE_3");
        when(statusUpdater.markCompleted(aeId)).thenReturn(true);
        doThrow(new RuntimeException("ledger write failed"))
            .when(ledgerWriter).writeCompletionEntry(any(), any(), any(), any(), anyBoolean(), any());

        assertThatCode(() -> listener.onCaseLifecycle(goalReachedEvent(caseId)))
            .doesNotThrowAnyException();

        verify(ledgerWriter).writeObserverFailureEntry(eq(aeId), eq(enrollmentId), eq(CtcaeGrade.GRADE_3));
    }

    @Test
    void writeCompletionEntry_and_fallback_both_throw_does_not_propagate() {
        UUID caseId = UUID.randomUUID();
        UUID aeId = UUID.randomUUID();
        UUID enrollmentId = UUID.randomUUID();
        mockInstanceWith(caseId, aeId, enrollmentId, "GRADE_4");
        when(statusUpdater.markCompleted(aeId)).thenReturn(true);
        doThrow(new RuntimeException("ledger write failed"))
            .when(ledgerWriter).writeCompletionEntry(any(), any(), any(), any(), anyBoolean(), any());
        doThrow(new RuntimeException("fallback write failed"))
            .when(ledgerWriter).writeObserverFailureEntry(any(), any(), any());

        assertThatCode(() -> listener.onCaseLifecycle(goalReachedEvent(caseId)))
            .doesNotThrowAnyException();
    }

    @Test
    void fireAsync_throws_after_ledger_written_no_failure_entry() {
        UUID caseId = UUID.randomUUID();
        UUID aeId = UUID.randomUUID();
        UUID enrollmentId = UUID.randomUUID();
        mockInstanceWith(caseId, aeId, enrollmentId, "GRADE_3");
        when(statusUpdater.markCompleted(aeId)).thenReturn(true);
        doThrow(new RuntimeException("fireAsync failed"))
            .when(completedEvents).fireAsync(any());

        assertThatCode(() -> listener.onCaseLifecycle(goalReachedEvent(caseId)))
            .doesNotThrowAnyException();

        verify(ledgerWriter, never()).writeObserverFailureEntry(any(), any(), any());
    }
}
