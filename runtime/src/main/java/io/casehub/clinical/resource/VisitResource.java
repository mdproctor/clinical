package io.casehub.clinical.resource;

import io.casehub.clinical.api.ClinicalGroups;
import io.casehub.clinical.api.model.VisitStatus;
import io.casehub.clinical.api.model.VisitType;
import io.casehub.clinical.entity.PatientEnrollment;
import io.casehub.clinical.entity.Visit;
import io.casehub.clinical.service.VisitLedgerWriter;
import io.casehub.platform.api.identity.CurrentPrincipal;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Path("/trials/{trialId}/sites/{siteId}/patients/{enrollmentId}/visits")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class VisitResource {

    @Inject CurrentPrincipal principal;
    @Inject VisitLedgerWriter ledgerWriter;

    public record ScheduleVisitRequest(
            @NotNull VisitType visitType,
            @NotNull Instant visitDate,
            @NotNull VisitStatus status,
            String notes) {}

    public record UpdateVisitRequest(VisitStatus status, String notes) {}

    @POST
    @Transactional
    @RolesAllowed({ClinicalGroups.INVESTIGATOR, ClinicalGroups.COORDINATOR})
    public Response create(@PathParam("trialId") UUID trialId,
                           @PathParam("siteId") UUID siteId,
                           @PathParam("enrollmentId") UUID enrollmentId,
                           @Valid ScheduleVisitRequest req,
                           @Context UriInfo uriInfo) {
        PatientEnrollment enrollment = PatientEnrollment.findByIdForTenant(enrollmentId, principal);
        if (enrollment == null || !enrollment.siteId.equals(siteId))
            return Response.status(Response.Status.NOT_FOUND).build();

        Visit visit = new Visit();
        visit.id = UUID.randomUUID();
        visit.tenantId = enrollment.tenantId;
        visit.enrollmentId = enrollmentId;
        visit.visitType = req.visitType();
        visit.visitDate = req.visitDate();
        visit.status = req.status();
        visit.notes = req.notes();
        visit.createdAt = Instant.now();
        visit.persist();

        ledgerWriter.writeEntry(visit);

        URI location = uriInfo.getAbsolutePathBuilder().path(visit.id.toString()).build();
        return Response.created(location).entity(visit).build();
    }

    @GET
    @RolesAllowed({ClinicalGroups.SPONSOR, ClinicalGroups.INVESTIGATOR,
                   ClinicalGroups.COORDINATOR, ClinicalGroups.MONITOR})
    public List<Visit> list(@PathParam("enrollmentId") UUID enrollmentId) {
        return Visit.listByEnrollment(enrollmentId, principal.tenancyId());
    }

    @GET
    @Path("/{visitId}")
    @RolesAllowed({ClinicalGroups.SPONSOR, ClinicalGroups.INVESTIGATOR,
                   ClinicalGroups.COORDINATOR, ClinicalGroups.MONITOR})
    public Response get(@PathParam("visitId") UUID visitId) {
        Visit visit = Visit.findByIdForTenant(visitId, principal);
        if (visit == null) return Response.status(Response.Status.NOT_FOUND).build();
        return Response.ok(visit).build();
    }

    @PATCH
    @Path("/{visitId}")
    @Transactional
    @RolesAllowed({ClinicalGroups.INVESTIGATOR, ClinicalGroups.COORDINATOR})
    public Response update(@PathParam("visitId") UUID visitId, UpdateVisitRequest req) {
        Visit visit = Visit.findByIdForTenant(visitId, principal);
        if (visit == null) return Response.status(Response.Status.NOT_FOUND).build();
        if (req.status() != null) visit.status = req.status();
        if (req.notes() != null) visit.notes = req.notes();
        return Response.ok(visit).build();
    }
}
