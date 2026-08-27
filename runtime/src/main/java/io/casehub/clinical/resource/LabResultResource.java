package io.casehub.clinical.resource;

import io.casehub.clinical.api.ClinicalGroups;
import io.casehub.clinical.api.model.AbnormalFlag;
import io.casehub.clinical.api.model.SpecimenType;
import io.casehub.clinical.entity.LabResult;
import io.casehub.clinical.entity.PatientEnrollment;
import io.casehub.clinical.service.LabResultLedgerWriter;
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

@Path("/trials/{trialId}/sites/{siteId}/patients/{enrollmentId}/lab-results")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class LabResultResource {

    @Inject CurrentPrincipal principal;
    @Inject LabResultLedgerWriter ledgerWriter;

    public record RecordLabResultRequest(
            @NotBlank String testName,
            @NotNull BigDecimal value,
            @NotBlank String unit,
            BigDecimal referenceRangeLow,
            BigDecimal referenceRangeHigh,
            @NotNull AbnormalFlag abnormalFlag,
            @NotNull SpecimenType specimenType,
            String performingLab,
            @NotNull Instant collectedAt,
            UUID visitId) {}

    @POST
    @Transactional
    @RolesAllowed({ClinicalGroups.INVESTIGATOR, ClinicalGroups.COORDINATOR})
    public Response create(@PathParam("trialId") UUID trialId,
                           @PathParam("siteId") UUID siteId,
                           @PathParam("enrollmentId") UUID enrollmentId,
                           @Valid RecordLabResultRequest req,
                           @Context UriInfo uriInfo) {
        PatientEnrollment enrollment = PatientEnrollment.findByIdForTenant(enrollmentId, principal);
        if (enrollment == null || !enrollment.siteId.equals(siteId))
            return Response.status(Response.Status.NOT_FOUND).build();

        LabResult lab = new LabResult();
        lab.id = UUID.randomUUID();
        lab.tenantId = enrollment.tenantId;
        lab.enrollmentId = enrollmentId;
        lab.visitId = req.visitId();
        lab.testName = req.testName();
        lab.value = req.value();
        lab.unit = req.unit();
        lab.referenceRangeLow = req.referenceRangeLow();
        lab.referenceRangeHigh = req.referenceRangeHigh();
        lab.abnormalFlag = req.abnormalFlag();
        lab.specimenType = req.specimenType();
        lab.performingLab = req.performingLab();
        lab.collectedAt = req.collectedAt();
        lab.createdAt = Instant.now();
        lab.persist();

        ledgerWriter.writeEntry(lab);

        URI location = uriInfo.getAbsolutePathBuilder().path(lab.id.toString()).build();
        return Response.created(location).entity(lab).build();
    }

    @GET
    @RolesAllowed({ClinicalGroups.SPONSOR, ClinicalGroups.INVESTIGATOR,
                   ClinicalGroups.COORDINATOR, ClinicalGroups.MONITOR})
    public List<LabResult> list(@PathParam("enrollmentId") UUID enrollmentId) {
        return LabResult.listByEnrollment(enrollmentId, principal.tenancyId());
    }

    @GET
    @Path("/{labId}")
    @RolesAllowed({ClinicalGroups.SPONSOR, ClinicalGroups.INVESTIGATOR,
                   ClinicalGroups.COORDINATOR, ClinicalGroups.MONITOR})
    public Response get(@PathParam("labId") UUID labId) {
        LabResult lab = LabResult.findByIdForTenant(labId, principal);
        if (lab == null) return Response.status(Response.Status.NOT_FOUND).build();
        return Response.ok(lab).build();
    }
}
