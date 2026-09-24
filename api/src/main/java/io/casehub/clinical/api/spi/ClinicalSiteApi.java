package io.casehub.clinical.api.spi;

import io.casehub.clinical.api.ClinicalGroups;
import io.casehub.clinical.api.view.AddSiteRequest;
import io.casehub.clinical.api.view.SiteView;
import io.casehub.platform.api.mcp.ContextParam;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PathParam;
import io.casehub.platform.api.mcp.PlatformMutation;
import io.casehub.platform.api.mcp.PlatformQuery;
import io.casehub.platform.api.mcp.RestPath;
import io.casehub.platform.api.mcp.RestStatus;
import jakarta.annotation.security.RolesAllowed;

import java.util.UUID;

@McpDomain(value = "clinical/sites", app = "clinical", basePath = "/trials/{trialId}/sites", summary = "Clinical trial site management")
public interface ClinicalSiteApi {

    @PlatformMutation("Add a site to a trial")
    @RestPath("/")
    @RestStatus(201)
    @RolesAllowed(ClinicalGroups.SPONSOR)
    SiteView addSite(@PathParam UUID trialId, AddSiteRequest request,
                     @ContextParam("tenancyId") String tenancyId);

    @PlatformQuery("Get a trial site by ID")
    @RestPath("/{siteId}")
    @RolesAllowed({ClinicalGroups.SPONSOR, ClinicalGroups.INVESTIGATOR,
                   ClinicalGroups.COORDINATOR, ClinicalGroups.MONITOR})
    SiteView getSite(@PathParam UUID trialId, @PathParam UUID siteId,
                     @ContextParam("tenancyId") String tenancyId);
}
