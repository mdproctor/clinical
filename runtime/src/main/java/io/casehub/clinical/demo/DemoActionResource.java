package io.casehub.clinical.demo;

import io.casehub.clinical.api.model.PiApprovalStatus;
import io.casehub.clinical.api.model.SusarOversightStatus;
import io.casehub.clinical.entity.AdverseEvent;
import io.casehub.clinical.entity.ProtocolDeviation;
import io.casehub.ledger.runtime.service.TrustScoreJob;
import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.qhorus.api.gateway.ChannelRef;
import io.casehub.qhorus.api.gateway.InboundHumanMessage;
import io.casehub.qhorus.runtime.channel.ChannelService;
import io.casehub.qhorus.runtime.gateway.ChannelGateway;
import io.casehub.work.api.WorkItem;
import io.casehub.work.api.spi.WorkItemStore;
import io.casehub.work.runtime.service.WorkItemService;
import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.jboss.logging.Logger;

/**
 * Dev-profile-only endpoints for the demo UI to trigger PI approval
 * and SUSAR gate approval without needing to know channel IDs or gate IDs.
 *
 * <p>These endpoints use real platform APIs (ChannelGateway, WorkItemService)
 * but abstract the plumbing so the UI sends a single POST with the domain ID.
 *
 * <p>Active only in dev profile via {@code @IfBuildProfile("dev")}.
 */
@Path("/demo")
@Produces(MediaType.APPLICATION_JSON)
@IfBuildProfile("dev")
public class DemoActionResource {

    private static final Logger LOG = Logger.getLogger(DemoActionResource.class);

    @Inject CurrentPrincipal principal;
    @Inject ChannelGateway channelGateway;
    @Inject ChannelService channelService;
    @Inject WorkItemService workItemService;
    @Inject WorkItemStore workItemStore;
    @Inject TrustScoreJob trustScoreJob;

    /**
     * Approve a protocol deviation on behalf of the PI.
     *
     * <p>Sends an APPROVED message through the real qhorus channel gateway,
     * which fires {@code MessageReceivedEvent} → {@code PiResponseListener}.
     * The deviation must be in COMMANDED state (PI obligation pending).
     */
    @POST
    @Path("/deviations/{deviationId}/approve-pi")
    @Transactional
    public Response approvePi(@PathParam("deviationId") UUID deviationId) {
        ProtocolDeviation dev = ProtocolDeviation.findByIdForTenant(deviationId, principal);
        if (dev == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", "Deviation not found", "deviationId", deviationId.toString()))
                    .build();
        }
        if (dev.piApprovalStatus != PiApprovalStatus.COMMANDED) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(Map.of(
                            "error", "Deviation is not in COMMANDED state",
                            "currentStatus", dev.piApprovalStatus.name(),
                            "deviationId", deviationId.toString()))
                    .build();
        }
        if (dev.piCommandChannelName == null) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(Map.of("error", "No PI command channel configured", "deviationId", deviationId.toString()))
                    .build();
        }

        var channel = channelService.findByName(dev.piCommandChannelName).orElse(null);
        if (channel == null) {
            LOG.errorf("PI oversight channel not found: %s", dev.piCommandChannelName);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "PI oversight channel not found", "channelName", dev.piCommandChannelName))
                    .build();
        }

        channelGateway.receiveHumanMessage(
                new ChannelRef(channel.id(), channel.name()),
                new InboundHumanMessage(
                        "demo-pi",
                        "{\"decision\":\"APPROVED\"}",
                        Instant.now(),
                        Map.of(),
                        deviationId.toString(),
                        null));

        LOG.infof("Demo PI approval sent for deviationId=%s via channel=%s", deviationId, channel.name());
        return Response.ok(Map.of(
                "deviationId", deviationId.toString(),
                "action", "PI_APPROVED",
                "channelName", channel.name(),
                "note", "MessageReceivedEvent fired — PiResponseListener will process async"))
                .build();
    }

    /**
     * Approve a SUSAR oversight gate for an adverse event.
     *
     * <p>Finds the gate WorkItem by scanning for callerRef containing
     * the SUSAR oversight case ID, then completes it via WorkItemService.
     * This triggers {@code ActionGateCompletionApplier} → {@code ActionGateApprovedEvent}.
     *
     * <p>After gate approval, triggers immediate Bayesian Beta trust score
     * recomputation (normally runs on 24h cron).
     */
    @POST
    @Path("/adverse-events/{aeId}/approve-susar-gate")
    @Transactional
    public Response approveSusarGate(@PathParam("aeId") UUID aeId) {
        AdverseEvent ae = AdverseEvent.findByIdForTenant(aeId, principal);
        if (ae == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", "Adverse event not found", "aeId", aeId.toString()))
                    .build();
        }
        if (ae.susarOversightCaseId == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of(
                            "error", "No SUSAR oversight case exists for this AE",
                            "aeId", aeId.toString(),
                            "susarOversightStatus", ae.susarOversightStatus.name()))
                    .build();
        }
        if (ae.susarOversightStatus == SusarOversightStatus.COMPLETED) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(Map.of(
                            "error", "SUSAR oversight already completed",
                            "aeId", aeId.toString()))
                    .build();
        }

        // Find the gate WorkItem by scanning for callerRef containing the case ID.
        // GateCallerRef.encode() produces "case:<caseId>/gate:<gateId>".
        String caseIdStr = ae.susarOversightCaseId.toString();
        WorkItem gateWorkItem = workItemStore.scanAll().stream()
                                                   .filter(wi -> wi.callerRef() != null && wi.callerRef().contains("case:" + caseIdStr))
                                                   .findFirst()
                                                   .orElse(null);

        if (gateWorkItem == null) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(Map.of(
                            "error", "No pending gate WorkItem found for SUSAR oversight case",
                            "aeId", aeId.toString(),
                            "susarOversightCaseId", caseIdStr))
                    .build();
        }

        String resolution = "{\"decision\":\"APPROVED\",\"approvedBy\":\"demo-investigator\"}";
        workItemService.completeFromSystem(gateWorkItem.id(), "demo-investigator", resolution);

        LOG.infof("Demo SUSAR gate approved for aeId=%s workItemId=%s", aeId, gateWorkItem.id());

        // Trigger immediate trust score recomputation (normally 24h cron)
        try {
            trustScoreJob.runComputation();
            LOG.info("Trust score recomputation completed after SUSAR gate approval");
        } catch (Exception e) {
            // Non-fatal — trust scores will update on next scheduled run
            LOG.warnf(e, "Trust score recomputation failed (non-fatal) after SUSAR gate approval for aeId=%s", aeId);
        }

        return Response.ok(Map.of(
                "aeId", aeId.toString(),
                "action", "SUSAR_GATE_APPROVED",
                "workItemId", gateWorkItem.id().toString(),
                "note", "ActionGateApprovedEvent fired — trust scores recomputed"))
                .build();
    }
}
