package io.casehub.clinical.resource;

import io.casehub.clinical.api.ClinicalGroups;
import io.casehub.clinical.api.model.AeOutcome;
import io.casehub.clinical.api.model.ConsentStatus;
import io.casehub.clinical.api.model.CriterionResult;
import io.casehub.clinical.api.model.CtcaeGrade;
import io.casehub.clinical.api.model.EnrollmentStatus;
import io.casehub.clinical.api.model.EventActuality;
import io.casehub.clinical.entity.AdverseEvent;
import io.casehub.clinical.entity.PatientEnrollment;
import io.casehub.clinical.entity.TrialSite;
import io.casehub.clinical.service.AdverseEventService;
import io.casehub.clinical.service.ConsentWithdrawalService;
import io.casehub.clinical.service.EligibilityScreeningService;
import io.casehub.clinical.service.PatientEnrollmentNotFoundException;
import io.casehub.clinical.service.WithdrawalResult;
import io.casehub.ledger.api.spi.LedgerEntryRepository;
import io.casehub.ledger.runtime.service.LedgerProvExportService;
import io.casehub.ledger.runtime.service.LedgerVerificationService;
import io.casehub.platform.api.identity.CurrentPrincipal;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Path("/trials/{trialId}/sites/{siteId}/patients")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PatientResource {

    @Inject AdverseEventService adverseEventService;
    @Inject ConsentWithdrawalService consentWithdrawalService;
    @Inject EligibilityScreeningService eligibilityScreeningService;
    @Inject LedgerProvExportService ledgerProvExportService;
    @Inject LedgerVerificationService ledgerVerificationService;
    @Inject LedgerEntryRepository ledgerEntryRepository;
    @Inject CurrentPrincipal principal;
    @Inject
            io.casehub.clinical.cbr.SiteEnrollmentAlertService siteEnrollmentAlertService;


    public record EnrollPatientRequest(@NotBlank String patientId) {}

    public record ScreenPatientRequest(
        @NotNull @Size(min = 1, message = "At least one criterion is required") List<CriterionResult> criteria
    ) {}

    public record ScreenResponse(String enrollmentStatus, String screeningResult) {}

    public record ReportAdverseEventRequest(
        @NotNull CtcaeGrade grade,
        @NotNull Instant occurredAt,
        EventActuality actuality,
        Boolean unexpected,
        Boolean suspected
    ) {}

    public record RegradeRequest(@NotNull CtcaeGrade grade, @NotBlank @Size(max = 500) String reason) {}

    public record GradeHistoryEntry(UUID id, String previousGrade, String newGrade,
                                    Instant changedAt, String changedBy, String reason) {}


    @GET
    @RolesAllowed({ClinicalGroups.SPONSOR, ClinicalGroups.INVESTIGATOR,
                   ClinicalGroups.COORDINATOR, ClinicalGroups.MONITOR})
    public List<PatientEnrollment> listForSite(@PathParam("trialId") UUID trialId,
                                               @PathParam("siteId") UUID siteId) {
        TrialSite site = TrialSite.findByIdForTenant(siteId, principal);
        if (site == null || !site.trialId.equals(trialId)) {return List.of();}
        return PatientEnrollment.list("siteId = ?1 and tenantId = ?2", siteId, site.tenantId);
    }

    @GET
    @Path("/{enrollmentId}/adverse-events")
    @RolesAllowed({ClinicalGroups.SPONSOR, ClinicalGroups.INVESTIGATOR,
                   ClinicalGroups.COORDINATOR, ClinicalGroups.MONITOR})
    public List<AdverseEvent> listAdverseEvents(@PathParam("trialId") UUID trialId,
                                                @PathParam("siteId") UUID siteId,
                                                @PathParam("enrollmentId") UUID enrollmentId) {
        PatientEnrollment enrollment = PatientEnrollment.findByIdForTenant(enrollmentId, principal);
        if (enrollment == null || !enrollment.siteId.equals(siteId)) {return List.of();}
        return AdverseEvent.list("enrollmentId = ?1 and tenantId = ?2", enrollmentId, enrollment.tenantId);
    }


    @POST
    @Transactional
    @RolesAllowed({ClinicalGroups.INVESTIGATOR, ClinicalGroups.COORDINATOR})
    public Response enroll(@PathParam("trialId") UUID trialId,
                           @PathParam("siteId") UUID siteId,
                           @Valid EnrollPatientRequest req,
                           @Context UriInfo uriInfo) {
        TrialSite site = TrialSite.findByIdForTenant(siteId, principal);
        if (site == null || !site.trialId.equals(trialId))
            return Response.status(Response.Status.NOT_FOUND).build();

        PatientEnrollment enrollment = new PatientEnrollment();
        enrollment.id = UUID.randomUUID();
        enrollment.tenantId = site.tenantId;
        enrollment.siteId = siteId;
        enrollment.patientId = req.patientId();
        enrollment.consentStatus = ConsentStatus.PENDING;
        enrollment.enrollmentStatus = EnrollmentStatus.CANDIDATE;
        enrollment.persist();
        try { siteEnrollmentAlertService.evaluate(siteId, site.trialId, enrollment.tenantId); } catch (Exception e) { /* advisory — enrollment completes regardless */ }

        URI location = uriInfo.getAbsolutePathBuilder().path(enrollment.id.toString()).build();
        return Response.created(location).build();
    }

    @GET
    @Path("/{enrollmentId}")
    @RolesAllowed({ClinicalGroups.SPONSOR, ClinicalGroups.INVESTIGATOR, ClinicalGroups.COORDINATOR, ClinicalGroups.MONITOR})
    public Response get(@PathParam("trialId") UUID trialId,
                        @PathParam("siteId") UUID siteId,
                        @PathParam("enrollmentId") UUID enrollmentId) {
        PatientEnrollment enrollment = PatientEnrollment.findByIdForTenant(enrollmentId, principal);
        if (enrollment == null || !enrollment.siteId.equals(siteId))
            return Response.status(Response.Status.NOT_FOUND).build();
        TrialSite site = TrialSite.findByIdForTenant(siteId, principal);
        if (site == null || !site.trialId.equals(trialId))
            return Response.status(Response.Status.NOT_FOUND).build();
        return Response.ok(enrollment).build();
    }

    @POST
    @Path("/{enrollmentId}/screen")
    @Transactional
    @RolesAllowed({ClinicalGroups.INVESTIGATOR, ClinicalGroups.COORDINATOR})
    public Response screen(@PathParam("trialId") UUID trialId,
                           @PathParam("siteId") UUID siteId,
                           @PathParam("enrollmentId") UUID enrollmentId,
                           @Valid ScreenPatientRequest req) {
        TrialSite site = TrialSite.findByIdForTenant(siteId, principal);
        if (site == null || !site.trialId.equals(trialId))
            return Response.status(Response.Status.NOT_FOUND).build();
        PatientEnrollment enrollment = PatientEnrollment.findByIdForTenant(enrollmentId, principal);
        if (enrollment == null || !enrollment.siteId.equals(siteId))
            return Response.status(Response.Status.NOT_FOUND).build();
        // Guard: reject re-screening — eligibility assessment must be a single recorded event (ICH E6(R3) §4.2)
        if (enrollment.screeningResult != null)
            return Response.status(Response.Status.CONFLICT)
                .entity("{\"error\":\"Patient already screened\"}")
                .build();

        eligibilityScreeningService.screen(enrollment, req.criteria());

        return Response.ok(new ScreenResponse(
            enrollment.enrollmentStatus.name(),
            enrollment.screeningResult != null ? enrollment.screeningResult.name() : null
        )).build();
    }

    @POST
    @Path("/{enrollmentId}/adverse-events")
    @RolesAllowed({ClinicalGroups.INVESTIGATOR, ClinicalGroups.COORDINATOR})
    public Response reportAdverseEvent(
            @PathParam("trialId") UUID trialId,
            @PathParam("siteId") UUID siteId,
            @PathParam("enrollmentId") UUID enrollmentId,
            @Valid ReportAdverseEventRequest req,
            @Context UriInfo uriInfo) {
        PatientEnrollment enrollment = PatientEnrollment.findByIdForTenant(enrollmentId, principal);
        if (enrollment == null || !enrollment.siteId.equals(siteId))
            return Response.status(Response.Status.NOT_FOUND).build();
        TrialSite site = TrialSite.findByIdForTenant(siteId, principal);
        if (site == null || !site.trialId.equals(trialId))
            return Response.status(Response.Status.NOT_FOUND).build();

        AdverseEvent ae = new AdverseEvent();
        ae.id = UUID.randomUUID();
        ae.enrollmentId = enrollmentId;
        ae.grade = req.grade();
        ae.actuality = req.actuality() != null ? req.actuality() : EventActuality.ACTUAL;
        ae.outcome = AeOutcome.ONGOING;
        ae.occurredAt = req.occurredAt();
        ae.unexpected = req.unexpected() != null ? req.unexpected() : false;
        ae.suspected  = req.suspected()  != null ? req.suspected()  : true;

        adverseEventService.reportAdverseEvent(ae);

        URI location = uriInfo.getAbsolutePathBuilder().path(ae.id.toString()).build();
        return Response.created(location).entity(ae).build();
    }

    @GET
    @Path("/{enrollmentId}/adverse-events/{aeId}")
    @RolesAllowed({ClinicalGroups.SPONSOR, ClinicalGroups.INVESTIGATOR, ClinicalGroups.COORDINATOR, ClinicalGroups.MONITOR})
    public Response getAdverseEvent(
            @PathParam("trialId") UUID trialId,
            @PathParam("siteId") UUID siteId,
            @PathParam("enrollmentId") UUID enrollmentId,
            @PathParam("aeId") UUID aeId) {
        PatientEnrollment enrollment = PatientEnrollment.findByIdForTenant(enrollmentId, principal);
        if (enrollment == null || !enrollment.siteId.equals(siteId))
            return Response.status(Response.Status.NOT_FOUND).build();
        TrialSite site = TrialSite.findByIdForTenant(siteId, principal);
        if (site == null || !site.trialId.equals(trialId))
            return Response.status(Response.Status.NOT_FOUND).build();
        AdverseEvent ae = AdverseEvent.findByIdForTenant(aeId, principal);
        if (ae == null || !ae.enrollmentId.equals(enrollmentId))
            return Response.status(Response.Status.NOT_FOUND).build();
        return Response.ok(ae).build();
    }

    @POST
    @Path("/{enrollmentId}/adverse-events/{aeId}/regrade")
    @RolesAllowed({ClinicalGroups.INVESTIGATOR, ClinicalGroups.COORDINATOR})
    @Transactional
    public Response regradeAdverseEvent(
            @PathParam("trialId") UUID trialId,
            @PathParam("siteId") UUID siteId,
            @PathParam("enrollmentId") UUID enrollmentId,
            @PathParam("aeId") UUID aeId,
            @Valid RegradeRequest req) {
        PatientEnrollment enrollment = PatientEnrollment.findByIdForTenant(enrollmentId, principal);
        if (enrollment == null || !enrollment.siteId.equals(siteId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        TrialSite site = TrialSite.findByIdForTenant(siteId, principal);
        if (site == null || !site.trialId.equals(trialId)) {return Response.status(Response.Status.NOT_FOUND).build();}
        AdverseEvent ae = AdverseEvent.findByIdForTenant(aeId, principal);
        if (ae == null || !ae.enrollmentId.equals(enrollmentId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        adverseEventService.regradeAdverseEvent(aeId, req.grade(), principal.actorId(), req.reason());

        ae = AdverseEvent.findById(aeId);
        return Response.ok(ae).build();
    }

    @GET
    @Path("/{enrollmentId}/adverse-events/{aeId}/grade-history")
    @RolesAllowed({ClinicalGroups.INVESTIGATOR, ClinicalGroups.COORDINATOR})
    public Response getGradeHistory(
            @PathParam("trialId") UUID trialId,
            @PathParam("siteId") UUID siteId,
            @PathParam("enrollmentId") UUID enrollmentId,
            @PathParam("aeId") UUID aeId) {
        PatientEnrollment enrollment = PatientEnrollment.findByIdForTenant(enrollmentId, principal);
        if (enrollment == null || !enrollment.siteId.equals(siteId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        TrialSite site = TrialSite.findByIdForTenant(siteId, principal);
        if (site == null || !site.trialId.equals(trialId)) {return Response.status(Response.Status.NOT_FOUND).build();}
        AdverseEvent ae = AdverseEvent.findByIdForTenant(aeId, principal);
        if (ae == null || !ae.enrollmentId.equals(enrollmentId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        var history = io.casehub.clinical.entity.AeGradeChange.findByAdverseEventId(aeId).stream()
                                                              .map(gc -> new GradeHistoryEntry(gc.id,
                                                                                               gc.previousGrade != null ? gc.previousGrade.name() : null,
                                                                                               gc.newGrade.name(), gc.changedAt, gc.changedBy, gc.reason))
                                                              .toList();
        return Response.ok(history).build();
    }


    @GET
    @Path("/{enrollmentId}/ledger/verify")
    @RolesAllowed({ClinicalGroups.SPONSOR, ClinicalGroups.INVESTIGATOR, ClinicalGroups.COORDINATOR, ClinicalGroups.MONITOR})
    public Response verifyLedger(
            @PathParam("trialId") UUID trialId,
            @PathParam("siteId") UUID siteId,
            @PathParam("enrollmentId") UUID enrollmentId) {
        PatientEnrollment enrollment = PatientEnrollment.findByIdForTenant(enrollmentId, principal);
        if (enrollment == null || !enrollment.siteId.equals(siteId))
            return Response.status(Response.Status.NOT_FOUND).build();
        TrialSite site = TrialSite.findByIdForTenant(siteId, principal);
        if (site == null || !site.trialId.equals(trialId))
            return Response.status(Response.Status.NOT_FOUND).build();
        // Ledger writers use "default" as tenantId placeholder (see CLAUDE.md ecosystem conventions).
        // Verification uses the same tenantId to locate entries in the in-memory / JPA store.
        String ledgerTenantId = "default";
        boolean valid = ledgerVerificationService.verify(enrollmentId, ledgerTenantId);
        String merkleRoot = null;
        try {
            merkleRoot = ledgerVerificationService.treeRoot(enrollmentId, ledgerTenantId);
        } catch (IllegalStateException ignored) {
            // no entries yet — merkleRoot stays null
        }
        return Response.ok(new LedgerVerifyResponse(valid, merkleRoot)).build();
    }

    public record LedgerVerifyResponse(boolean valid, String merkleRoot) {}

    @POST
    @Path("/{enrollmentId}/withdraw-consent")
    @RolesAllowed(ClinicalGroups.INVESTIGATOR)
    public Response withdrawConsent(
            @PathParam("trialId") UUID trialId,
            @PathParam("siteId") UUID siteId,
            @PathParam("enrollmentId") UUID enrollmentId) {
        PatientEnrollment enrollment = PatientEnrollment.findByIdForTenant(enrollmentId, principal);
        if (enrollment == null || !enrollment.siteId.equals(siteId))
            return Response.status(Response.Status.NOT_FOUND).build();
        TrialSite site = TrialSite.findByIdForTenant(siteId, principal);
        if (site == null || !site.trialId.equals(trialId))
            return Response.status(Response.Status.NOT_FOUND).build();
        try {
            WithdrawalResult result = consentWithdrawalService.withdraw(enrollmentId, principal.tenancyId());
            if (result == WithdrawalResult.ALREADY_WITHDRAWN) {
                return Response.status(Response.Status.CONFLICT)
                        .entity("Consent already withdrawn for enrollment " + enrollmentId).build();
            }
            return Response.noContent().build();
        } catch (PatientEnrollmentNotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
    }

    @GET
    @Path("/{enrollmentId}/audit/prov")
    @Produces("application/ld+json")
    @RolesAllowed({ClinicalGroups.SPONSOR, ClinicalGroups.INVESTIGATOR, ClinicalGroups.COORDINATOR, ClinicalGroups.MONITOR})
    public Response getAuditProv(
            @PathParam("trialId") UUID trialId,
            @PathParam("siteId") UUID siteId,
            @PathParam("enrollmentId") UUID enrollmentId) {
        PatientEnrollment enrollment = PatientEnrollment.findByIdForTenant(enrollmentId, principal);
        if (enrollment == null || !enrollment.siteId.equals(siteId))
            return Response.status(Response.Status.NOT_FOUND).build();
        TrialSite site = TrialSite.findByIdForTenant(siteId, principal);
        if (site == null || !site.trialId.equals(trialId))
            return Response.status(Response.Status.NOT_FOUND).build();
        try {
            String jsonLd = ledgerProvExportService.exportSubject(enrollmentId, principal.tenancyId());
            return Response.ok(jsonLd).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
    }

    @GET
    @Path("/{enrollmentId}/audit/entries/{entryId}/proof")
    @RolesAllowed({ClinicalGroups.SPONSOR, ClinicalGroups.INVESTIGATOR, ClinicalGroups.COORDINATOR, ClinicalGroups.MONITOR})
    public Response getMerkleProof(
            @PathParam("trialId") UUID trialId,
            @PathParam("siteId") UUID siteId,
            @PathParam("enrollmentId") UUID enrollmentId,
            @PathParam("entryId") UUID entryId) {
        PatientEnrollment enrollment = PatientEnrollment.findByIdForTenant(enrollmentId, principal);
        if (enrollment == null || !enrollment.siteId.equals(siteId))
            return Response.status(Response.Status.NOT_FOUND).build();
        TrialSite site = TrialSite.findByIdForTenant(siteId, principal);
        if (site == null || !site.trialId.equals(trialId))
            return Response.status(Response.Status.NOT_FOUND).build();
        // Verify the entry belongs to this patient — prevents cross-patient audit access
        var ledgerEntry = ledgerEntryRepository.findEntryById(entryId, principal.tenancyId()).orElse(null);
        if (ledgerEntry == null || !enrollmentId.equals(ledgerEntry.subjectId))
            return Response.status(Response.Status.NOT_FOUND).build();
        try {
            var proof = ledgerVerificationService.inclusionProof(entryId, principal.tenancyId());
            return Response.ok(proof).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
    }
}
