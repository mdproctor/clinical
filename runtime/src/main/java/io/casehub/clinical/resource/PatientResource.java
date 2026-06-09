package io.casehub.clinical.resource;

import io.casehub.clinical.api.model.AeOutcome;
import io.casehub.clinical.api.model.ConsentStatus;
import io.casehub.clinical.api.model.CtcaeGrade;
import io.casehub.clinical.api.model.EnrollmentStatus;
import io.casehub.clinical.api.model.EventActuality;
import io.casehub.clinical.entity.AdverseEvent;
import io.casehub.clinical.entity.PatientEnrollment;
import io.casehub.clinical.entity.TrialSite;
import io.casehub.clinical.service.AdverseEventService;
import io.casehub.platform.api.identity.CurrentPrincipal;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;

@Path("/trials/{trialId}/sites/{siteId}/patients")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PatientResource {

    @Inject AdverseEventService adverseEventService;
    @Inject CurrentPrincipal principal;

    public record EnrollPatientRequest(@NotBlank String patientId) {}

    public record ReportAdverseEventRequest(
        @NotNull CtcaeGrade grade,
        @NotNull Instant occurredAt,
        EventActuality actuality
    ) {}

    @POST
    @Transactional
    public Response enroll(@PathParam("trialId") UUID trialId,
                           @PathParam("siteId") UUID siteId,
                           @Valid EnrollPatientRequest req,
                           @Context UriInfo uriInfo) {
        TrialSite site = TrialSite.findById(siteId);
        if (site == null || !site.trialId.equals(trialId))
            return Response.status(Response.Status.NOT_FOUND).build();

        PatientEnrollment enrollment = new PatientEnrollment();
        enrollment.id = UUID.randomUUID();
        enrollment.tenantId = principal.tenancyId();
        enrollment.siteId = siteId;
        enrollment.patientId = req.patientId();
        enrollment.consentStatus = ConsentStatus.PENDING;
        enrollment.enrollmentStatus = EnrollmentStatus.CANDIDATE;
        enrollment.persist();

        URI location = uriInfo.getAbsolutePathBuilder().path(enrollment.id.toString()).build();
        return Response.created(location).build();
    }

    @GET
    @Path("/{enrollmentId}")
    public Response get(@PathParam("trialId") UUID trialId,
                        @PathParam("siteId") UUID siteId,
                        @PathParam("enrollmentId") UUID enrollmentId) {
        PatientEnrollment enrollment = PatientEnrollment.findById(enrollmentId);
        if (enrollment == null || !enrollment.siteId.equals(siteId))
            return Response.status(Response.Status.NOT_FOUND).build();
        TrialSite site = TrialSite.findById(siteId);
        if (site == null || !site.trialId.equals(trialId))
            return Response.status(Response.Status.NOT_FOUND).build();
        return Response.ok(enrollment).build();
    }

    @POST
    @Path("/{enrollmentId}/adverse-events")
    public Response reportAdverseEvent(
            @PathParam("trialId") UUID trialId,
            @PathParam("siteId") UUID siteId,
            @PathParam("enrollmentId") UUID enrollmentId,
            @Valid ReportAdverseEventRequest req,
            @Context UriInfo uriInfo) {
        PatientEnrollment enrollment = PatientEnrollment.findById(enrollmentId);
        if (enrollment == null || !enrollment.siteId.equals(siteId))
            return Response.status(Response.Status.NOT_FOUND).build();
        TrialSite site = TrialSite.findById(siteId);
        if (site == null || !site.trialId.equals(trialId))
            return Response.status(Response.Status.NOT_FOUND).build();

        AdverseEvent ae = new AdverseEvent();
        ae.id = UUID.randomUUID();
        ae.enrollmentId = enrollmentId;
        ae.grade = req.grade();
        ae.actuality = req.actuality() != null ? req.actuality() : EventActuality.ACTUAL;
        ae.outcome = AeOutcome.ONGOING;
        ae.occurredAt = req.occurredAt();

        adverseEventService.reportAdverseEvent(ae);

        URI location = uriInfo.getAbsolutePathBuilder().path(ae.id.toString()).build();
        return Response.created(location).entity(ae).build();
    }
}
