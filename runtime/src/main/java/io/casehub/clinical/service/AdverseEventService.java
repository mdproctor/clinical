package io.casehub.clinical.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.clinical.entity.AdverseEvent;
import io.casehub.clinical.ledger.AdverseEventLedgerEntry;
import io.casehub.ledger.api.model.ActorType;
import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.runtime.repository.LedgerEntryRepository;
import io.casehub.work.runtime.model.WorkItemCreateRequest;
import io.casehub.work.runtime.model.WorkItemPriority;
import io.casehub.work.runtime.service.WorkItemService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class AdverseEventService {

    @Inject WorkItemService workItemService;
    @Inject LedgerEntryRepository ledgerRepo;
    @Inject ObjectMapper objectMapper;

    @Transactional
    public void reportAdverseEvent(AdverseEvent ae) {
        ae.reportedAt = Instant.now();
        ae.slaDeadline = ae.reportedAt.plus(ae.grade.sla().orElseThrow());

        var workItem = workItemService.create(WorkItemCreateRequest.builder()
            .title("Adverse Event — " + ae.grade.label())
            .description("Grade " + ae.grade.label() + " AE for enrollment " + ae.enrollmentId +
                ". GCP SLA: " + ae.grade.sla().orElseThrow().toHours() + "h from " + ae.reportedAt)
            .category("adverse-event")
            .formKey("adverse-event-review")
            .priority(priority(ae))
            .candidateGroups(candidateGroups(ae))
            .createdBy("system")
            .payload(payload(ae))
            .claimDeadline(ae.slaDeadline)
            .build());
        ae.workItemId = workItem.id;
        ae.persist();

        writeLedgerEntry(ae);
    }

    private void writeLedgerEntry(AdverseEvent ae) {
        AdverseEventLedgerEntry entry = new AdverseEventLedgerEntry();
        entry.id = UUID.randomUUID();
        entry.subjectId = ae.id;
        entry.sequenceNumber = 1;
        entry.entryType = LedgerEntryType.EVENT;
        entry.actorId = "system";
        entry.actorType = ActorType.SYSTEM;
        entry.actorRole = "AdverseEventReporter";
        entry.occurredAt = Instant.now();
        entry.adverseEventId = ae.id;
        entry.enrollmentId = ae.enrollmentId;
        entry.ctcaeGrade = ae.grade.name();
        entry.reportedAt = ae.reportedAt;
        entry.slaDeadline = ae.slaDeadline;
        ledgerRepo.save(entry);
    }

    private WorkItemPriority priority(AdverseEvent ae) {
        return switch (ae.grade) {
            case GRADE_5 -> WorkItemPriority.URGENT;
            case GRADE_3, GRADE_4 -> WorkItemPriority.HIGH;
            default -> WorkItemPriority.MEDIUM;
        };
    }

    private String candidateGroups(AdverseEvent ae) {
        return switch (ae.grade) {
            case GRADE_1, GRADE_2 -> "safety-officers";
            default -> "dsmb,safety-officers";
        };
    }

    private String payload(AdverseEvent ae) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                "enrollmentId", ae.enrollmentId.toString(),
                "grade", ae.grade.name(),
                "occurredAt", ae.occurredAt.toString()
            ));
        } catch (JsonProcessingException e) {
            return "{\"enrollmentId\":\"" + ae.enrollmentId + "\"}";
        }
    }
}
