package io.casehub.clinical.api.spi;

import io.casehub.clinical.api.ClinicalGroups;
import io.casehub.clinical.api.view.DeviationView;
import io.casehub.clinical.api.view.ReportDeviationRequest;
import io.casehub.platform.api.mcp.ContextParam;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PathParam;
import io.casehub.platform.api.mcp.PlatformMutation;
import io.casehub.platform.api.mcp.PlatformQuery;
import io.casehub.platform.api.mcp.RestPath;
import io.casehub.platform.api.mcp.RestStatus;
import jakarta.annotation.security.RolesAllowed;

import java.util.UUID;

@McpDomain(value = "clinical/deviations", app = "clinical", basePath = "/trials/{trialId}/sites/{siteId}/deviations", summary = "Protocol deviation recording and tracking")
public interface ClinicalDeviationApi {

    @PlatformMutation("Report a protocol deviation")
    @RestPath("/")
    @RestStatus(201)
    @RolesAllowed({ClinicalGroups.INVESTIGATOR, ClinicalGroups.COORDINATOR})
    DeviationView reportDeviation(@PathParam UUID trialId, @PathParam UUID siteId,
                                   ReportDeviationRequest request,
                                   @ContextParam("tenancyId") String tenancyId);

    @PlatformQuery("Get a protocol deviation by ID")
    @RestPath("/{deviationId}")
    @RolesAllowed({ClinicalGroups.SPONSOR, ClinicalGroups.INVESTIGATOR,
                   ClinicalGroups.COORDINATOR, ClinicalGroups.MONITOR})
    DeviationView getDeviation(@PathParam UUID trialId, @PathParam UUID siteId,
                                @PathParam UUID deviationId,
                                @ContextParam("tenancyId") String tenancyId);
}
