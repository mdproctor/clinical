package io.casehub.clinical.api.spi;

import io.casehub.clinical.api.ClinicalGroups;
import io.casehub.clinical.api.model.EvaluateScreenRequest;
import io.casehub.clinical.api.view.EnrollPatientRequest;
import io.casehub.clinical.api.view.PatientEnrollmentView;
import io.casehub.clinical.api.view.ScreenPatientRequest;
import io.casehub.clinical.api.view.ScreenResponse;
import io.casehub.platform.api.mcp.ContextParam;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PathParam;
import io.casehub.platform.api.mcp.PlatformMutation;
import io.casehub.platform.api.mcp.PlatformQuery;
import io.casehub.platform.api.mcp.RestPath;
import io.casehub.platform.api.mcp.RestStatus;
import jakarta.annotation.security.RolesAllowed;

import java.util.List;
import java.util.UUID;

@McpDomain(value = "clinical/patients", app = "clinical", basePath = "/trials/{trialId}/sites/{siteId}/patients", summary = "Patient enrollment and demographics in clinical trials")
public interface ClinicalPatientApi {

    @PlatformQuery("List patients enrolled at a site")
    @RestPath("/")
    @RolesAllowed({ClinicalGroups.SPONSOR, ClinicalGroups.INVESTIGATOR,
                   ClinicalGroups.COORDINATOR, ClinicalGroups.MONITOR})
    List<PatientEnrollmentView> listPatients(@PathParam UUID trialId, @PathParam UUID siteId,
                                              @ContextParam("tenancyId") String tenancyId);

    @PlatformMutation("Enroll a patient at a site")
    @RestPath("/")
    @RestStatus(201)
    @RolesAllowed({ClinicalGroups.INVESTIGATOR, ClinicalGroups.COORDINATOR})
    PatientEnrollmentView enrollPatient(@PathParam UUID trialId, @PathParam UUID siteId,
                                         EnrollPatientRequest request,
                                         @ContextParam("tenancyId") String tenancyId);

    @PlatformQuery("Get a patient enrollment by ID")
    @RestPath("/{enrollmentId}")
    @RolesAllowed({ClinicalGroups.SPONSOR, ClinicalGroups.INVESTIGATOR,
                   ClinicalGroups.COORDINATOR, ClinicalGroups.MONITOR})
    PatientEnrollmentView getPatient(@PathParam UUID trialId, @PathParam UUID siteId,
                                      @PathParam UUID enrollmentId,
                                      @ContextParam("tenancyId") String tenancyId);

    @PlatformMutation("Screen a patient against eligibility criteria")
    @RestPath("/{enrollmentId}/screen")
    @RolesAllowed({ClinicalGroups.INVESTIGATOR, ClinicalGroups.COORDINATOR})
    ScreenResponse screenPatient(@PathParam UUID trialId, @PathParam UUID siteId,
                                  @PathParam UUID enrollmentId, ScreenPatientRequest request,
                                  @ContextParam("tenancyId") String tenancyId);

    @PlatformMutation("Evaluate protocol criteria and screen a patient")
    @RestPath("/{enrollmentId}/evaluate-and-screen")
    @RolesAllowed({ClinicalGroups.INVESTIGATOR, ClinicalGroups.COORDINATOR})
    ScreenResponse evaluateAndScreen(@PathParam UUID trialId, @PathParam UUID siteId,
                                      @PathParam UUID enrollmentId, EvaluateScreenRequest request,
                                      @ContextParam("tenancyId") String tenancyId);
}
