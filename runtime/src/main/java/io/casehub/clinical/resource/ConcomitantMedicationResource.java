package io.casehub.clinical.resource;

import io.casehub.clinical.api.ClinicalGroups;
import io.casehub.clinical.api.model.MedicationFrequency;
import io.casehub.clinical.api.model.MedicationRoute;
import io.casehub.clinical.entity.ConcomitantMedication;
import io.casehub.clinical.entity.PatientEnrollment;
import io.casehub.clinical.service.ConcomitantMedicationLedgerWriter;
import io.casehub.platform.api.identity.CurrentPrincipal;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Path("/trials/{trialId}/sites/{siteId}/patients/{enrollmentId}/medications")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ConcomitantMedicationResource {

    @Inject CurrentPrincipal principal;
    @Inject ConcomitantMedicationLedgerWriter ledgerWriter;

    public record RecordMedicationRequest(
            @NotBlank String medicationName,
            String indication,
            @NotBlank String dose,
            @NotBlank String unit,
            @NotNull MedicationRoute route,
            @NotNull MedicationFrequency frequency,
            @NotNull LocalDate startDate,
            LocalDate endDate,
            boolean ongoing) {}

    public record UpdateMedicationRequest(LocalDate endDate, Boolean ongoing) {}

    @POST
    @Transactional
    @RolesAllowed({ClinicalGroups.INVESTIGATOR, ClinicalGroups.COORDINATOR})
    public Response create(@PathParam("trialId") UUID trialId,
                           @PathParam("siteId") UUID siteId,
                           @PathParam("enrollmentId") UUID enrollmentId,
                           @Valid RecordMedicationRequest req,
                           @Context UriInfo uriInfo) {
        PatientEnrollment enrollment = PatientEnrollment.findByIdForTenant(enrollmentId, principal);
        if (enrollment == null || !enrollment.siteId.equals(siteId))
            return Response.status(Response.Status.NOT_FOUND).build();

        ConcomitantMedication med = new ConcomitantMedication();
        med.id = UUID.randomUUID();
        med.tenantId = enrollment.tenantId;
        med.enrollmentId = enrollmentId;
        med.medicationName = req.medicationName();
        med.indication = req.indication();
        med.dose = req.dose();
        med.unit = req.unit();
        med.route = req.route();
        med.frequency = req.frequency();
        med.startDate = req.startDate();
        med.endDate = req.endDate();
        med.ongoing = req.ongoing();
        med.createdAt = Instant.now();
        med.persist();

        ledgerWriter.writeEntry(med);

        URI location = uriInfo.getAbsolutePathBuilder().path(med.id.toString()).build();
        return Response.created(location).entity(med).build();
    }

    @GET
    @RolesAllowed({ClinicalGroups.SPONSOR, ClinicalGroups.INVESTIGATOR,
                   ClinicalGroups.COORDINATOR, ClinicalGroups.MONITOR})
    public List<ConcomitantMedication> list(@PathParam("enrollmentId") UUID enrollmentId) {
        return ConcomitantMedication.listByEnrollment(enrollmentId, principal.tenancyId());
    }

    @GET
    @Path("/{medId}")
    @RolesAllowed({ClinicalGroups.SPONSOR, ClinicalGroups.INVESTIGATOR,
                   ClinicalGroups.COORDINATOR, ClinicalGroups.MONITOR})
    public Response get(@PathParam("medId") UUID medId) {
        ConcomitantMedication med = ConcomitantMedication.findByIdForTenant(medId, principal);
        if (med == null) return Response.status(Response.Status.NOT_FOUND).build();
        return Response.ok(med).build();
    }

    @PATCH
    @Path("/{medId}")
    @Transactional
    @RolesAllowed({ClinicalGroups.INVESTIGATOR, ClinicalGroups.COORDINATOR})
    public Response update(@PathParam("medId") UUID medId, UpdateMedicationRequest req) {
        ConcomitantMedication med = ConcomitantMedication.findByIdForTenant(medId, principal);
        if (med == null) return Response.status(Response.Status.NOT_FOUND).build();
        if (req.endDate() != null) med.endDate = req.endDate();
        if (req.ongoing() != null) med.ongoing = req.ongoing();
        return Response.ok(med).build();
    }
}
