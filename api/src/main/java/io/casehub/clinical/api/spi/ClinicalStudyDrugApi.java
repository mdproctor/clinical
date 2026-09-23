package io.casehub.clinical.api.spi;

import io.casehub.clinical.api.ClinicalGroups;
import io.casehub.clinical.api.view.RecordDrugAdminRequest;
import io.casehub.clinical.api.view.StudyDrugView;
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

@McpDomain(value = "clinical/study-drug", app = "clinical", basePath = "/trials/{trialId}/sites/{siteId}/patients/{enrollmentId}/study-drug")
public interface ClinicalStudyDrugApi {

    @PlatformMutation("Record a study drug administration")
    @RestPath("/")
    @RestStatus(201)
    @RolesAllowed({ClinicalGroups.INVESTIGATOR, ClinicalGroups.COORDINATOR})
    StudyDrugView recordDrugAdmin(@PathParam UUID trialId, @PathParam UUID siteId,
                                   @PathParam UUID enrollmentId, RecordDrugAdminRequest request,
                                   @ContextParam("tenancyId") String tenancyId);

    @PlatformQuery("List study drug administrations for a patient")
    @RestPath("/")
    @RolesAllowed({ClinicalGroups.SPONSOR, ClinicalGroups.INVESTIGATOR,
                   ClinicalGroups.COORDINATOR, ClinicalGroups.MONITOR})
    List<StudyDrugView> listDrugAdmins(@PathParam UUID trialId, @PathParam UUID siteId,
                                        @PathParam UUID enrollmentId,
                                        @ContextParam("tenancyId") String tenancyId);

    @PlatformQuery("Get a study drug administration by ID")
    @RestPath("/{adminId}")
    @RolesAllowed({ClinicalGroups.SPONSOR, ClinicalGroups.INVESTIGATOR,
                   ClinicalGroups.COORDINATOR, ClinicalGroups.MONITOR})
    StudyDrugView getDrugAdmin(@PathParam UUID trialId, @PathParam UUID siteId,
                                @PathParam UUID enrollmentId, @PathParam UUID adminId,
                                @ContextParam("tenancyId") String tenancyId);
}
