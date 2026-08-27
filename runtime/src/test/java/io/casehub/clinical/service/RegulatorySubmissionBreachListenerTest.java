package io.casehub.clinical.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.casehub.clinical.api.model.AeOutcome;
import io.casehub.clinical.api.model.CtcaeGrade;
import io.casehub.clinical.api.model.EventActuality;
import io.casehub.clinical.api.model.RegulatorySubmissionStatus;
import io.casehub.clinical.entity.AdverseEvent;
import io.casehub.work.api.WorkItemLifecycleEvent;
import io.casehub.work.api.WorkItem;
import io.casehub.work.api.WorkItemStatus;
import io.casehub.work.engine.PlanItemCallerRef;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@QuarkusTest
class RegulatorySubmissionBreachListenerTest {

    @Inject RegulatorySubmissionBreachListener listener;
    @InjectMock RegulatorySubmissionLedgerWriter ledgerWriter;

    @Test
    void escalated_event_for_regulatory_submission_sets_deadline_missed() {
        UUID caseId = UUID.randomUUID();
        UUID aeId = persistAe(caseId, RegulatorySubmissionStatus.PENDING);

        listener.onWorkItemLifecycle(escalatedEvent(
                PlanItemCallerRef.encode(caseId, UUID.randomUUID().toString())));

        assertThat(findAe(aeId).regulatorySubmissionStatus)
                .isEqualTo(RegulatorySubmissionStatus.DEADLINE_MISSED);
        verify(ledgerWriter).writeBreachEntry(any());
    }

    @Test
    void non_engine_callerRef_is_ignored() {
        // "clinical:adverse-event/{id}" format — CallerRef.parse() returns null
        listener.onWorkItemLifecycle(escalatedEvent("clinical:adverse-event/" + UUID.randomUUID()));
        verify(ledgerWriter, never()).writeBreachEntry(any());
    }

    @Test
    void unrelated_case_id_is_ignored() {
        listener.onWorkItemLifecycle(escalatedEvent(
                PlanItemCallerRef.encode(UUID.randomUUID(), UUID.randomUUID().toString())));
        verify(ledgerWriter, never()).writeBreachEntry(any());
    }

    @Test
    void duplicate_escalated_event_is_idempotent() {
        UUID caseId = UUID.randomUUID();
        String callerRef = PlanItemCallerRef.encode(caseId, UUID.randomUUID().toString());
        persistAe(caseId, RegulatorySubmissionStatus.PENDING);

        listener.onWorkItemLifecycle(escalatedEvent(callerRef));
        listener.onWorkItemLifecycle(escalatedEvent(callerRef));

        verify(ledgerWriter, times(1)).writeBreachEntry(any());
    }

    @Test
    void already_filed_status_is_protected() {
        UUID caseId = UUID.randomUUID();
        UUID aeId = persistAe(caseId, RegulatorySubmissionStatus.FILED);

        listener.onWorkItemLifecycle(escalatedEvent(
                PlanItemCallerRef.encode(caseId, UUID.randomUUID().toString())));

        assertThat(findAe(aeId).regulatorySubmissionStatus)
                .isEqualTo(RegulatorySubmissionStatus.FILED);
        verify(ledgerWriter, never()).writeBreachEntry(any());
    }

    @Test
    void non_escalated_status_is_ignored() {
        UUID caseId = UUID.randomUUID();
        persistAe(caseId, RegulatorySubmissionStatus.PENDING);

        // WorkItemLifecycleEvent.of() copies workItem.status into event.status() —
        // must set the workItem status to COMPLETED, not ESCALATED.
        WorkItem wi = workItem(PlanItemCallerRef.encode(caseId, UUID.randomUUID().toString()))
                .toBuilder().status(WorkItemStatus.COMPLETED).build();
        WorkItemLifecycleEvent event = WorkItemLifecycleEvent.of("COMPLETED", wi, "system", null);
        listener.onWorkItemLifecycle(event);

        verify(ledgerWriter, never()).writeBreachEntry(any());
    }

    @Test
    void wire_reconstructed_event_null_source_is_ignored() {
        // fromWire() creates an event where source() returns null
        WorkItemLifecycleEvent event = WorkItemLifecycleEvent.fromWire(
                "io.casehub.work.workitem.escalated",
                "/workitems/" + UUID.randomUUID(),
                UUID.randomUUID().toString(),
                UUID.randomUUID(), WorkItemStatus.ESCALATED,
                Instant.now(), "system", null, null, null, null, "test-tenant", null, null, null, null, null);
        listener.onWorkItemLifecycle(event);
        verify(ledgerWriter, never()).writeBreachEntry(any());
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    @Transactional
    UUID persistAe(UUID caseId, RegulatorySubmissionStatus status) {
        AdverseEvent ae = new AdverseEvent();
        ae.id = UUID.randomUUID();
        ae.enrollmentId = UUID.randomUUID();
        ae.grade = CtcaeGrade.GRADE_5;
        ae.unexpected = true;
        ae.suspected = true;
        ae.actuality = EventActuality.ACTUAL;
        ae.outcome = AeOutcome.ONGOING;
        ae.occurredAt = Instant.now();
        ae.reportedAt = Instant.now();
        ae.tenantId = "test-tenant";
        ae.regulatorySubmissionCaseId = caseId;
        ae.regulatorySubmissionStatus = status;
        ae.persist();
        return ae.id;
    }

    @Transactional
    AdverseEvent findAe(UUID aeId) {
        return AdverseEvent.findById(aeId);
    }

    private WorkItemLifecycleEvent escalatedEvent(String callerRef) {
        return WorkItemLifecycleEvent.of("ESCALATED", workItem(callerRef), "system", null);
    }

    private WorkItem workItem(String callerRef) {
        return WorkItem.builder()
                .id(UUID.randomUUID())
                .callerRef(callerRef)
                .status(WorkItemStatus.ESCALATED)
                .tenancyId("test-tenant")
                .build();
    }
}
