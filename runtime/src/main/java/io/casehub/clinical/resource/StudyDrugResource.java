package io.casehub.clinical.resource;

import io.casehub.clinical.api.ClinicalGroups;
import io.casehub.clinical.api.model.DrugAdminStatus;
import io.casehub.clinical.api.model.MedicationRoute;
import io.casehub.clinical.entity.PatientEnrollment;
import io.casehub.clinical.entity.StudyDrugAdministration;
import io.casehub.clinical.service.StudyDrugLedgerWriter;
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
import java.util.List;
import java.util.UUID;

@Path("/trials/{trialId}/sites/{siteId}/patients/{enrollmentId}/study-drug")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class StudyDrugResource {

    @Inject CurrentPrincipal principal;
    @Inject StudyDrugLedgerWriter ledgerWriter;

    public record RecordDrugAdminRequest(
            @NotBlank String drugName,
            @NotBlank String dose,
            @NotBlank String unit,
            @NotNull MedicationRoute route,
            @NotNull Instant administeredAt,
            @NotBlank String administeredBy,
            String batchNumber,
            @NotNull DrugAdminStatus status) {}

    @POST
    @Transactional
    @RolesAllowed({ClinicalGroups.INVESTIGATOR, ClinicalGroups.COORDINATOR})
    public Response create(@PathParam("trialId") UUID trialId,
                           @PathParam("siteId") UUID siteId,
                           @PathParam("enrollmentId") UUID enrollmentId,
                           @Valid RecordDrugAdminRequest req,
                           @Context UriInfo uriInfo) {
        PatientEnrollment enrollment = PatientEnrollment.findByIdForTenant(enrollmentId, principal);
        if (enrollment == null || !enrollment.siteId.equals(siteId))
            return Response.status(Response.Status.NOT_FOUND).build();

        StudyDrugAdministration drug = new StudyDrugAdministration();
        drug.id = UUID.randomUUID();
        drug.tenantId = enrollment.tenantId;
        drug.enrollmentId = enrollmentId;
        drug.drugName = req.drugName();
        drug.dose = req.dose();
        drug.unit = req.unit();
        drug.route = req.route();
        drug.administeredAt = req.administeredAt();
        drug.administeredBy = req.administeredBy();
        drug.batchNumber = req.batchNumber();
        drug.status = req.status();
        drug.createdAt = Instant.now();
        drug.persist();

        ledgerWriter.writeEntry(drug);

        URI location = uriInfo.getAbsolutePathBuilder().path(drug.id.toString()).build();
        return Response.created(location).entity(drug).build();
    }

    @GET
    @RolesAllowed({ClinicalGroups.SPONSOR, ClinicalGroups.INVESTIGATOR,
                   ClinicalGroups.COORDINATOR, ClinicalGroups.MONITOR})
    public List<StudyDrugAdministration> list(@PathParam("enrollmentId") UUID enrollmentId) {
        return StudyDrugAdministration.listByEnrollment(enrollmentId, principal.tenancyId());
    }

    @GET
    @Path("/{adminId}")
    @RolesAllowed({ClinicalGroups.SPONSOR, ClinicalGroups.INVESTIGATOR,
                   ClinicalGroups.COORDINATOR, ClinicalGroups.MONITOR})
    public Response get(@PathParam("adminId") UUID adminId) {
        StudyDrugAdministration drug = StudyDrugAdministration.findByIdForTenant(adminId, principal);
        if (drug == null) return Response.status(Response.Status.NOT_FOUND).build();
        return Response.ok(drug).build();
    }
}
