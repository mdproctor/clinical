package io.casehub.clinical.resource;

import io.casehub.clinical.api.model.TrialStatus;
import io.casehub.clinical.api.model.TrialPhase;
import io.casehub.clinical.entity.ClinicalTrial;
import io.casehub.clinical.service.TrialActivationService;
import io.casehub.platform.api.identity.CurrentPrincipal;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import java.net.URI;
import java.util.UUID;

@Path("/trials")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class TrialResource {

    @Inject TrialActivationService trialActivationService;
    @Inject CurrentPrincipal principal;

    public record SponsorConfigRequest(
        @Size(max = 64) String connectorId,
        @Size(max = 2048) String destination
    ) {}

    public record RegisterTrialRequest(
        @NotBlank String protocolId,
        @NotNull TrialPhase phase,
        @NotBlank String sponsor,
        @Positive int targetEnrollment,
        @Size(max = 64) String sponsorNotificationConnectorId,
        @Size(max = 2048) String sponsorNotificationDestination
    ) {}

    @POST
    @Transactional
    public Response register(@Valid RegisterTrialRequest req, @Context UriInfo uriInfo) {
        ClinicalTrial trial = new ClinicalTrial();
        trial.id = UUID.randomUUID();
        trial.protocolId = req.protocolId();
        trial.phase = req.phase();
        trial.sponsor = req.sponsor();
        trial.targetEnrollment = req.targetEnrollment();
        trial.status = TrialStatus.PLANNING;
        trial.sponsorNotificationConnectorId = req.sponsorNotificationConnectorId();
        trial.sponsorNotificationDestination = req.sponsorNotificationDestination();
        trial.tenantId = principal.tenancyId();
        trial.persist();

        URI location = uriInfo.getAbsolutePathBuilder().path(trial.id.toString()).build();
        return Response.created(location).build();
    }

    @GET
    @Path("/{id}")
    public Response get(@PathParam("id") UUID id) {
        ClinicalTrial trial = ClinicalTrial.findById(id);
        if (trial == null) return Response.status(Response.Status.NOT_FOUND).build();
        return Response.ok(trial).build();
    }

    @PATCH
    @Path("/{id}/sponsor-config")
    @Transactional
    public Response updateSponsorConfig(@PathParam("id") UUID id, @NotNull @Valid SponsorConfigRequest req) {
        ClinicalTrial trial = ClinicalTrial.findById(id);
        if (trial == null) return Response.status(Response.Status.NOT_FOUND).build();
        trial.sponsorNotificationConnectorId = req.connectorId();
        trial.sponsorNotificationDestination = req.destination();
        return Response.noContent().build();
    }

    // WILDCARD: POST with no body — overrides class-level APPLICATION_JSON
    @POST
    @Path("/{id}/activate")
    @Consumes(MediaType.WILDCARD)
    public Response activate(@PathParam("id") UUID id) {
        try {
            trialActivationService.activate(id);
            return Response.noContent().build();
        } catch (TrialActivationService.TrialNotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND).build();
        } catch (TrialActivationService.TrialNotInPlanningStatusException e) {
            return Response.status(Response.Status.CONFLICT).build();
        }
    }
}
