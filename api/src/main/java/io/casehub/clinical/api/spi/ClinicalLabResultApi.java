package io.casehub.clinical.api.spi;

import io.casehub.clinical.api.ClinicalGroups;
import io.casehub.clinical.api.view.LabResultView;
import io.casehub.clinical.api.view.RecordLabResultRequest;
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

@McpDomain(value = "clinical/lab-results", app = "clinical", basePath = "/trials/{trialId}/sites/{siteId}/patients/{enrollmentId}/lab-results")
public interface ClinicalLabResultApi {

    @PlatformMutation("Record a lab result")
    @RestPath("/")
    @RestStatus(201)
    @RolesAllowed({ClinicalGroups.INVESTIGATOR, ClinicalGroups.COORDINATOR})
    LabResultView recordLabResult(@PathParam UUID trialId, @PathParam UUID siteId,
                                   @PathParam UUID enrollmentId, RecordLabResultRequest request,
                                   @ContextParam("tenancyId") String tenancyId);

    @PlatformQuery("List lab results for a patient")
    @RestPath("/")
    @RolesAllowed({ClinicalGroups.SPONSOR, ClinicalGroups.INVESTIGATOR,
                   ClinicalGroups.COORDINATOR, ClinicalGroups.MONITOR})
    List<LabResultView> listLabResults(@PathParam UUID trialId, @PathParam UUID siteId,
                                        @PathParam UUID enrollmentId,
                                        @ContextParam("tenancyId") String tenancyId);

    @PlatformQuery("Get a lab result by ID")
    @RestPath("/{labId}")
    @RolesAllowed({ClinicalGroups.SPONSOR, ClinicalGroups.INVESTIGATOR,
                   ClinicalGroups.COORDINATOR, ClinicalGroups.MONITOR})
    LabResultView getLabResult(@PathParam UUID trialId, @PathParam UUID siteId,
                                @PathParam UUID enrollmentId, @PathParam UUID labId,
                                @ContextParam("tenancyId") String tenancyId);
}
