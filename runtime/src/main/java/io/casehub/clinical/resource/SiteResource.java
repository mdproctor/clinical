package io.casehub.clinical.resource;

import io.casehub.clinical.api.model.SiteStatus;
import io.casehub.clinical.entity.ClinicalTrial;
import io.casehub.clinical.entity.TrialSite;
import io.casehub.platform.api.identity.CurrentPrincipal;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import java.net.URI;
import java.util.UUID;

@Path("/trials/{trialId}/sites")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SiteResource {

    @Inject CurrentPrincipal principal;

    public record AddSiteRequest(@NotBlank String investigatorId) {}

    @POST
    @Transactional
    public Response add(@PathParam("trialId") UUID trialId,
                        @Valid AddSiteRequest req,
                        @Context UriInfo uriInfo) {
        if (ClinicalTrial.findById(trialId) == null)
            return Response.status(Response.Status.NOT_FOUND).build();

        TrialSite site = new TrialSite();
        site.id = UUID.randomUUID();
        site.tenantId = principal.tenancyId();
        site.trialId = trialId;
        site.investigatorId = req.investigatorId();
        site.status = SiteStatus.PENDING;
        site.persist();

        URI location = uriInfo.getAbsolutePathBuilder().path(site.id.toString()).build();
        return Response.created(location).build();
    }

    @GET
    @Path("/{siteId}")
    public Response get(@PathParam("trialId") UUID trialId,
                        @PathParam("siteId") UUID siteId) {
        TrialSite site = TrialSite.findById(siteId);
        if (site == null || !site.trialId.equals(trialId))
            return Response.status(Response.Status.NOT_FOUND).build();
        return Response.ok(site).build();
    }
}
