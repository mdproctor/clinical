package io.casehub.clinical.service;

import io.casehub.clinical.api.ProtocolDeviationResolvedEvent;
import io.casehub.clinical.api.model.EscalationRequirement;
import io.casehub.clinical.api.model.IrbDecision;
import io.casehub.clinical.api.model.PiApprovalStatus;
import io.casehub.clinical.api.spi.IrbCommitteeAssignment;
import io.casehub.clinical.api.spi.IrbCommitteeAssignmentPolicy;
import io.casehub.clinical.api.spi.IrbCommitteeContext;
import io.casehub.clinical.entity.IrbApproval;
import io.casehub.clinical.entity.ProtocolDeviation;
import io.casehub.clinical.entity.TrialSite;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Observes ProtocolDeviationResolvedEvent and starts an IRB deviation review engine case when
 * the deviation requires IRB review and the PI has approved it.
 *
 * <p>Three-phase pattern: Phase 1 creates IrbApproval + evaluates IrbCommitteeAssignmentPolicy,
 * Phase 2 starts the engine case (outside TX), Phase 3 writes deviation.engineCaseId.
 */
@ApplicationScoped
public class IrbDeviationCaseService {

    private static final Logger LOG = Logger.getLogger(IrbDeviationCaseService.class);

    @Inject ClinicalDeviationCaseHub caseHub;
    @Inject IrbCommitteeAssignmentPolicy committeePolicy;

    public void onDeviationResolved(@ObservesAsync ProtocolDeviationResolvedEvent event) {
        if (event.escalationRequirement() != EscalationRequirement.IRB_REVIEW) return;
        if (event.terminalStatus() != PiApprovalStatus.APPROVED) return;

        try {
            Map<String, Object> initialContext = prepareAndCreateApproval(event);
            UUID caseId = caseHub.startCase(initialContext).toCompletableFuture().join();
            persistDeviationCaseId(event.deviationId(), caseId);
        } catch (Exception e) {
            LOG.errorf(e, "IrbDeviationCaseService: IRB case start failed for deviationId=%s", event.deviationId());
        }
    }

    @Transactional
    Map<String, Object> prepareAndCreateApproval(ProtocolDeviationResolvedEvent event) {
        TrialSite site = TrialSite.findById(event.siteId());
        UUID trialId = site != null ? site.trialId : null;

        IrbCommitteeContext committeeCtx = new IrbCommitteeContext(
                event.deviationId(), event.siteId(), trialId, event.severity());
        IrbCommitteeAssignment assignment = committeePolicy.evaluate(committeeCtx);

        IrbApproval approval = new IrbApproval();
        approval.id = UUID.randomUUID();
        approval.tenantId = event.tenantId();
        approval.siteId = event.siteId();
        approval.deviationId = event.deviationId();
        approval.reviewType = "PROTOCOL_DEVIATION";
        approval.committeeId = assignment.committeeId();
        approval.decisionDeadline = Instant.now().plus(Duration.ofHours(72));
        approval.decision = IrbDecision.PENDING;
        approval.persist();

        Map<String, Object> ctx = new HashMap<>();
        ctx.put("deviationId", event.deviationId().toString());
        ctx.put("siteId", event.siteId().toString());
        ctx.put("severity", event.severity().name());
        ctx.put("escalationRequirement", event.escalationRequirement().name());
        ctx.put("irbConsultationRequired", true);
        ctx.put("committeeId", assignment.committeeId());
        ctx.put("candidateGroups", assignment.candidateGroups());
        return ctx;
    }

    @Transactional
    void persistDeviationCaseId(UUID deviationId, UUID caseId) {
        ProtocolDeviation deviation = ProtocolDeviation.findById(deviationId);
        if (deviation == null) {
            LOG.warnf("IrbDeviationCaseService: ProtocolDeviation not found for deviationId=%s", deviationId);
            return;
        }
        deviation.engineCaseId = caseId;
    }
}
