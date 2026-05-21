package io.casehub.clinical.resource;

import io.casehub.clinical.api.model.TrialStatus;
import io.casehub.clinical.api.model.TrialPhase;
import io.casehub.clinical.entity.ClinicalTrial;
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
}
