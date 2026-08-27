package io.casehub.clinical.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.clinical.api.AdverseEventReportedEvent;
import io.casehub.clinical.api.spi.AdverseEventContext;
import io.casehub.clinical.api.spi.AdverseEventEscalationPolicy;
import io.casehub.clinical.api.spi.AdverseEventEscalationRequirements;
import io.casehub.clinical.entity.AdverseEvent;
import io.casehub.clinical.entity.PatientEnrollment;
import io.casehub.clinical.entity.TrialSite;
import io.casehub.clinical.memory.ClinicalMemoryService;
import io.casehub.work.api.WorkItemCreateRequest;
import io.casehub.work.api.WorkItemPriority;
import io.casehub.work.runtime.service.WorkItemService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Status;
import jakarta.transaction.Synchronization;
import jakarta.transaction.TransactionSynchronizationRegistry;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class AdverseEventService {

    @Inject
    WorkItemService                                  workItemService;
    @Inject
    AdverseEventLedgerWriter                         ledgerWriter;
    @Inject
    ObjectMapper                                     objectMapper;
    @Inject
    AdverseEventEscalationPolicy                     policy;
    @Inject
    Event<AdverseEventReportedEvent>                 reportedEvents;
    @Inject
    ClinicalMemoryService                            memoryService;
    @Inject
    TransactionSynchronizationRegistry               txSync;
    @Inject
    io.casehub.clinical.cbr.AeTrajectoryAlertService aeTrajectoryAlertService;
    @Inject
    AeGradeChangeLedgerWriter                        gradeChangeLedgerWriter;
    @Inject
    Event<io.casehub.clinical.api.AeGradeChangedEvent> gradeChangedEvents;


    @Transactional
    public void reportAdverseEvent(AdverseEvent ae) {
        ae.reportedAt  = Instant.now();
        ae.slaDeadline = ae.reportedAt.plus(ae.grade.sla().orElseThrow());

        PatientEnrollment enrollment = PatientEnrollment.findById(ae.enrollmentId);
        UUID              siteId     = enrollment != null ? enrollment.siteId : null;
        ae.tenantId = enrollment != null ? enrollment.tenantId : "default";

        TrialSite site    = siteId != null ? TrialSite.findById(siteId) : null;
        UUID      trialId = site != null ? site.trialId : null;
        AdverseEventEscalationRequirements requirements =
                policy.evaluate(new AdverseEventContext(ae.id, ae.enrollmentId, siteId, ae.grade));

        if (!requirements.engineCaseRequired()) {
            var workItem = workItemService.create(WorkItemCreateRequest.builder()
                                                                       .title("Adverse Event — " + ae.grade.label())
                                                                       .description("Grade " + ae.grade.label() + " AE for enrollment "
                                                                                    + ae.enrollmentId + ". GCP SLA: "
                                                                                    + ae.grade.sla().orElseThrow().toHours() + "h from " + ae.reportedAt)
                                                                       .types(java.util.List.of("adverse-event"))
                                                                       .formKey("adverse-event-review")
                                                                       .priority(priority(ae))
                                                                       .candidateGroups(requirements.candidateGroups())
                                                                       .createdBy("system")
                                                                       .payload(payload(ae))
                                                                       .claimDeadline(ae.slaDeadline)
                                                                       .build());
            ae.workItemId = workItem.id();
        }

        ae.persist();
        ledgerWriter.writeReportEntry(ae);
        memoryService.storeAeReport(ae.id, ae.enrollmentId, siteId, trialId, ae.grade, ae.tenantId);
        try {aeTrajectoryAlertService.evaluate(ae.id, ae.tenantId);} catch (Exception e) {
            org.jboss.logging.Logger.getLogger(AdverseEventService.class).warnf(e, "Trajectory alert evaluation failed for aeId=%s", ae.id);
        }

        io.casehub.clinical.entity.AeGradeChange initial = new io.casehub.clinical.entity.AeGradeChange();
        initial.id             = UUID.randomUUID();
        initial.adverseEventId = ae.id;
        initial.previousGrade  = null;
        initial.newGrade       = ae.grade;
        initial.changedAt      = ae.reportedAt;
        initial.changedBy      = "system";
        initial.reason         = "Initial report";
        initial.persist();

        if (requirements.engineCaseRequired()) {
            var event = new AdverseEventReportedEvent(
                    ae.id, ae.enrollmentId, siteId, ae.grade, ae.reportedAt, ae.tenantId);
            txSync.registerInterposedSynchronization(new Synchronization() {
                @Override
                public void beforeCompletion() {}

                @Override
                public void afterCompletion(int status) {
                    if (status == Status.STATUS_COMMITTED) {
                        reportedEvents.fireAsync(event);
                    }
                }
            });
        }
    }

    @Transactional
    public void regradeAdverseEvent(UUID aeId, io.casehub.clinical.api.model.CtcaeGrade newGrade, String changedBy, String reason) {
        AdverseEvent ae = AdverseEvent.findById(aeId);
        if (ae == null) {return;}
        if (newGrade == ae.grade) {return;}

        io.casehub.clinical.api.model.CtcaeGrade previousGrade = ae.grade;

        io.casehub.clinical.entity.AeGradeChange change = new io.casehub.clinical.entity.AeGradeChange();
        change.id             = UUID.randomUUID();
        change.adverseEventId = aeId;
        change.previousGrade  = previousGrade;
        change.newGrade       = newGrade;
        change.changedAt      = Instant.now();
        change.changedBy      = changedBy;
        change.reason         = reason;
        change.persist();

        ae.grade = newGrade;

        if (newGrade.ordinal() > previousGrade.ordinal()) {
            Instant newDeadline = Instant.now().plus(newGrade.sla().orElseThrow());
            if (newDeadline.isBefore(ae.slaDeadline)) {
                ae.slaDeadline = newDeadline;
            }
        }

        gradeChangeLedgerWriter.writeGradeChangeEntry(ae, previousGrade, reason);

        PatientEnrollment enrollment = PatientEnrollment.findById(ae.enrollmentId);
        UUID              siteId     = enrollment != null ? enrollment.siteId : null;
        TrialSite         site       = siteId != null ? TrialSite.findById(siteId) : null;
        UUID              trialId    = site != null ? site.trialId : null;

        memoryService.storeAeRegrade(aeId, ae.enrollmentId, siteId, trialId,
                                     previousGrade, newGrade, ae.tenantId);

        var event = new io.casehub.clinical.api.AeGradeChangedEvent(
                aeId, ae.enrollmentId, siteId, previousGrade, newGrade,
                change.changedAt, changedBy, ae.tenantId);
        txSync.registerInterposedSynchronization(new Synchronization() {
            @Override
            public void beforeCompletion() {}

            @Override
            public void afterCompletion(int status) {
                if (status == Status.STATUS_COMMITTED) {
                    gradeChangedEvents.fireAsync(event);
                }
            }
        });
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
