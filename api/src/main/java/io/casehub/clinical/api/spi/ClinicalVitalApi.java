package io.casehub.clinical.api.spi;

import io.casehub.clinical.api.ClinicalGroups;
import io.casehub.clinical.api.view.RecordVitalSignRequest;
import io.casehub.clinical.api.view.VitalSignView;
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

@McpDomain(value = "clinical/vitals", app = "clinical", basePath = "/trials/{trialId}/sites/{siteId}/patients/{enrollmentId}/vitals", summary = "Patient vital signs recording and queries")
public interface ClinicalVitalApi {

    @PlatformMutation("Record a vital sign measurement")
    @RestPath("/")
    @RestStatus(201)
    @RolesAllowed({ClinicalGroups.INVESTIGATOR, ClinicalGroups.COORDINATOR})
    VitalSignView recordVital(@PathParam UUID trialId, @PathParam UUID siteId,
                              @PathParam UUID enrollmentId, RecordVitalSignRequest request,
                              @ContextParam("tenancyId") String tenancyId);

    @PlatformQuery("List vital signs for a patient")
    @RestPath("/")
    @RolesAllowed({ClinicalGroups.SPONSOR, ClinicalGroups.INVESTIGATOR,
                   ClinicalGroups.COORDINATOR, ClinicalGroups.MONITOR})
    List<VitalSignView> listVitals(@PathParam UUID trialId, @PathParam UUID siteId,
                                    @PathParam UUID enrollmentId,
                                    @ContextParam("tenancyId") String tenancyId);

    @PlatformQuery("Get a vital sign by ID")
    @RestPath("/{vitalId}")
    @RolesAllowed({ClinicalGroups.SPONSOR, ClinicalGroups.INVESTIGATOR,
                   ClinicalGroups.COORDINATOR, ClinicalGroups.MONITOR})
    VitalSignView getVital(@PathParam UUID trialId, @PathParam UUID siteId,
                           @PathParam UUID enrollmentId, @PathParam UUID vitalId,
                           @ContextParam("tenancyId") String tenancyId);
}
