package io.casehub.clinical.resource;

import io.casehub.clinical.api.ClinicalGroups;
import io.casehub.clinical.api.model.VitalType;
import io.casehub.clinical.entity.PatientEnrollment;
import io.casehub.clinical.entity.VitalSign;
import io.casehub.clinical.service.VitalSignLedgerWriter;
import io.casehub.platform.api.identity.CurrentPrincipal;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Path("/trials/{trialId}/sites/{siteId}/patients/{enrollmentId}/vitals")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class VitalSignResource {

    @Inject CurrentPrincipal principal;
    @Inject VitalSignLedgerWriter ledgerWriter;

    public record RecordVitalSignRequest(
            @NotNull VitalType type,
            @NotNull BigDecimal value,
            @NotBlank String unit,
            @NotNull Instant measuredAt,
            UUID visitId) {}

    @POST
    @Transactional
    @RolesAllowed({ClinicalGroups.INVESTIGATOR, ClinicalGroups.COORDINATOR})
    public Response create(@PathParam("trialId") UUID trialId,
                           @PathParam("siteId") UUID siteId,
                           @PathParam("enrollmentId") UUID enrollmentId,
                           @Valid RecordVitalSignRequest req,
                           @Context UriInfo uriInfo) {
        PatientEnrollment enrollment = PatientEnrollment.findByIdForTenant(enrollmentId, principal);
        if (enrollment == null || !enrollment.siteId.equals(siteId))
            return Response.status(Response.Status.NOT_FOUND).build();

        VitalSign vital = new VitalSign();
        vital.id = UUID.randomUUID();
        vital.tenantId = enrollment.tenantId;
        vital.enrollmentId = enrollmentId;
        vital.visitId = req.visitId();
        vital.type = req.type();
        vital.value = req.value();
        vital.unit = req.unit();
        vital.measuredAt = req.measuredAt();
        vital.createdAt = Instant.now();
        vital.persist();

        ledgerWriter.writeEntry(vital);

        URI location = uriInfo.getAbsolutePathBuilder().path(vital.id.toString()).build();
        return Response.created(location).entity(vital).build();
    }

    @GET
    @RolesAllowed({ClinicalGroups.SPONSOR, ClinicalGroups.INVESTIGATOR,
                   ClinicalGroups.COORDINATOR, ClinicalGroups.MONITOR})
    public List<VitalSign> list(@PathParam("enrollmentId") UUID enrollmentId) {
        return VitalSign.listByEnrollment(enrollmentId, principal.tenancyId());
    }

    @GET
    @Path("/{vitalId}")
    @RolesAllowed({ClinicalGroups.SPONSOR, ClinicalGroups.INVESTIGATOR,
                   ClinicalGroups.COORDINATOR, ClinicalGroups.MONITOR})
    public Response get(@PathParam("vitalId") UUID vitalId) {
        VitalSign vital = VitalSign.findByIdForTenant(vitalId, principal);
        if (vital == null) return Response.status(Response.Status.NOT_FOUND).build();
        return Response.ok(vital).build();
    }
}
