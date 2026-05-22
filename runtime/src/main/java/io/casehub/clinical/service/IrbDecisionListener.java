package io.casehub.clinical.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.clinical.api.IrbApprovalResolvedEvent;
import io.casehub.clinical.api.model.IrbDecision;
import io.casehub.clinical.entity.IrbApproval;
import io.casehub.work.runtime.event.WorkItemLifecycleEvent;
import io.casehub.work.runtime.model.WorkItem;
import io.casehub.work.runtime.model.WorkItemStatus;
import io.casehub.workadapter.CallerRef;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.jboss.logging.Logger;

/**
 * Bridges IRB WorkItem lifecycle events to the IrbApproval domain entity and ledger.
 *
 * <p>Discriminates IRB WorkItems by presence of {@code deviationId} in the payload
 * (set by the YAML binding's inputMapping). Non-IRB WorkItems have no deviationId
 * and are silently ignored.
 *
 * <p>EXPIRED path: WorkItemLifecycleAdapter calls markFaulted() — no outputMapping fires.
 * This listener signals the case directly with irbConsultation: {decision: EXPIRED}
 * so the irb-decided goal (.irbConsultation != null) completes the case.
 */
@ApplicationScoped
public class IrbDecisionListener {

    private static final Logger LOG = Logger.getLogger(IrbDecisionListener.class);

    @Inject IrbApprovalLedgerWriter ledgerWriter;
    @Inject Event<IrbApprovalResolvedEvent> resolvedEvents;
    @Inject ClinicalDeviationCaseHub caseHub;
    @Inject ObjectMapper objectMapper;

    @Transactional
    public void onWorkItemLifecycle(@ObservesAsync WorkItemLifecycleEvent event) {
        if (!(event.source() instanceof WorkItem workItem)) return;

        UUID deviationId = extractDeviationId(workItem);
        if (deviationId == null) return;

        IrbDecision decision = resolveDecision(event.status(), workItem);
        if (decision == null) return;

        IrbApproval approval = IrbApproval
                .find("deviationId = ?1 and decision = 'PENDING'", deviationId)
                .firstResult();
        if (approval == null) {
            LOG.warnf("No PENDING IrbApproval for deviationId=%s — already resolved?", deviationId);
            return;
        }

        approval.decision = decision;
        approval.persist();

        if (event.status() == WorkItemStatus.EXPIRED) {
            CallerRef ref = CallerRef.parse(workItem.callerRef);
            if (ref != null) {
                caseHub.signal(ref.caseId(), "irbConsultation", Map.of(
                        "decision", "EXPIRED",
                        "committeeId", approval.committeeId,
                        "decidedAt", Instant.now().toString()));
            }
        }

        ledgerWriter.writeDecisionEntry(approval);

        resolvedEvents.fireAsync(new IrbApprovalResolvedEvent(
                approval.id, deviationId, approval.siteId, decision, Instant.now()));
    }

    private UUID extractDeviationId(WorkItem workItem) {
        if (workItem.payload == null) return null;
        try {
            JsonNode node = objectMapper.readTree(workItem.payload);
            JsonNode idNode = node.get("deviationId");
            if (idNode == null || idNode.isNull()) return null;
            return UUID.fromString(idNode.asText());
        } catch (Exception e) {
            return null;
        }
    }

    private IrbDecision resolveDecision(WorkItemStatus status, WorkItem workItem) {
        return switch (status) {
            case COMPLETED -> parseDecisionFromResolution(workItem.resolution);
            case EXPIRED   -> IrbDecision.EXPIRED;
            default        -> null;
        };
    }

    private IrbDecision parseDecisionFromResolution(String resolution) {
        if (resolution == null) return null;
        try {
            JsonNode node = objectMapper.readTree(resolution);
            JsonNode d = node.get("decision");
            if (d == null || d.isNull()) return null;
            return IrbDecision.valueOf(d.asText().toUpperCase());
        } catch (Exception e) {
            LOG.warnf("Could not parse IrbDecision from resolution: %s", resolution);
            return null;
        }
    }
}
