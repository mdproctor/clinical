package io.casehub.clinical.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.clinical.api.AdverseEventReportedEvent;
import io.casehub.clinical.api.spi.AdverseEventContext;
import io.casehub.clinical.api.spi.AdverseEventEscalationPolicy;
import io.casehub.clinical.api.spi.AdverseEventEscalationRequirements;
import io.casehub.clinical.entity.AdverseEvent;
import io.casehub.clinical.entity.PatientEnrollment;
import io.casehub.work.runtime.model.WorkItemCreateRequest;
import io.casehub.work.runtime.model.WorkItemPriority;
import io.casehub.work.runtime.service.WorkItemService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class AdverseEventService {

    @Inject WorkItemService workItemService;
    @Inject AdverseEventLedgerWriter ledgerWriter;
    @Inject ObjectMapper objectMapper;
    @Inject AdverseEventEscalationPolicy policy;
    @Inject Event<AdverseEventReportedEvent> reportedEvents;

    @Transactional
    public void reportAdverseEvent(AdverseEvent ae) {
        ae.reportedAt = Instant.now();
        ae.slaDeadline = ae.reportedAt.plus(ae.grade.sla().orElseThrow());

        UUID siteId = resolveSiteId(ae.enrollmentId);
        AdverseEventEscalationRequirements requirements =
                policy.evaluate(new AdverseEventContext(ae.id, ae.enrollmentId, siteId, ae.grade));

        if (!requirements.engineCaseRequired()) {
            var workItem = workItemService.create(WorkItemCreateRequest.builder()
                    .title("Adverse Event — " + ae.grade.label())
                    .description("Grade " + ae.grade.label() + " AE for enrollment "
                            + ae.enrollmentId + ". GCP SLA: "
                            + ae.grade.sla().orElseThrow().toHours() + "h from " + ae.reportedAt)
                    .category("adverse-event")
                    .formKey("adverse-event-review")
                    .priority(priority(ae))
                    .candidateGroups(requirements.candidateGroups())
                    .createdBy("system")
                    .payload(payload(ae))
                    .claimDeadline(ae.slaDeadline)
                    .build());
            ae.workItemId = workItem.id;
        }
        // Grade 3+: ae.workItemId remains null — engine creates WorkItems via humanTask bindings

        ae.persist();
        ledgerWriter.writeReportEntry(ae);

        if (requirements.engineCaseRequired()) {
            reportedEvents.fireAsync(new AdverseEventReportedEvent(
                    ae.id, ae.enrollmentId, siteId, ae.grade, ae.reportedAt));
        }
    }

    private UUID resolveSiteId(UUID enrollmentId) {
        PatientEnrollment enrollment = PatientEnrollment.findById(enrollmentId);
        return enrollment != null ? enrollment.siteId : null;
    }

    private WorkItemPriority priority(AdverseEvent ae) {
        return switch (ae.grade) {
            case GRADE_5 -> WorkItemPriority.URGENT;
            case GRADE_3, GRADE_4 -> WorkItemPriority.HIGH;
            default -> WorkItemPriority.MEDIUM;
        };
    }

    private String payload(AdverseEvent ae) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "enrollmentId", ae.enrollmentId.toString(),
                    "grade", ae.grade.name(),
                    "occurredAt", ae.occurredAt.toString()));
        } catch (JsonProcessingException e) {
            return "{\"enrollmentId\":\"" + ae.enrollmentId + "\"}";
        }
    }
}
