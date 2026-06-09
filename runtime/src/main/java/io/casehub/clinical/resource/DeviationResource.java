package io.casehub.clinical.resource;

import io.casehub.clinical.api.model.DeviationSeverity;
import io.casehub.clinical.api.model.PiApprovalStatus;
import io.casehub.clinical.entity.ProtocolDeviation;
import io.casehub.clinical.entity.TrialSite;
import io.casehub.clinical.service.ProtocolDeviationService;
import io.casehub.platform.api.identity.CurrentPrincipal;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import java.net.URI;
import java.util.UUID;

@Path("/trials/{trialId}/sites/{siteId}/deviations")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class DeviationResource {

    @Inject ProtocolDeviationService deviationService;
    @Inject CurrentPrincipal principal;

    public record ReportDeviationRequest(
        @NotBlank String deviationType,
        @NotNull DeviationSeverity severity
    ) {}

    @POST
    @Transactional
    public Response reportDeviation(
            @PathParam("trialId") UUID trialId,
            @PathParam("siteId") UUID siteId,
            @Valid ReportDeviationRequest req,
            @Context UriInfo uriInfo) {
        TrialSite site = TrialSite.findById(siteId);
        if (site == null || !site.trialId.equals(trialId))
            return Response.status(Response.Status.NOT_FOUND).build();

        ProtocolDeviation deviation = new ProtocolDeviation();
        deviation.id = UUID.randomUUID();
        deviation.tenantId = principal.tenancyId();
        deviation.siteId = siteId;
        deviation.deviationType = req.deviationType();
        deviation.severity = req.severity();
        deviation.piApprovalStatus = PiApprovalStatus.PENDING;

        deviationService.reportDeviation(deviation);

        URI location = uriInfo.getAbsolutePathBuilder().path(deviation.id.toString()).build();
        return Response.created(location).entity(deviation).build();
    }

    @GET
    @Path("/{deviationId}")
    public Response getDeviation(
            @PathParam("trialId") UUID trialId,
            @PathParam("siteId") UUID siteId,
            @PathParam("deviationId") UUID deviationId) {
        ProtocolDeviation dev = ProtocolDeviation.findById(deviationId);
        if (dev == null || !dev.siteId.equals(siteId))
            return Response.status(Response.Status.NOT_FOUND).build();
        TrialSite site = TrialSite.findById(siteId);
        if (site == null || !site.trialId.equals(trialId))
            return Response.status(Response.Status.NOT_FOUND).build();
        return Response.ok(dev).build();
    }
}
