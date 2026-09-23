package io.casehub.clinical.api.spi;

import io.casehub.clinical.api.ClinicalGroups;
import io.casehub.clinical.api.view.RegisterTrialRequest;
import io.casehub.clinical.api.view.SponsorConfigRequest;
import io.casehub.clinical.api.view.TrialListView;
import io.casehub.clinical.api.view.TrialView;
import io.casehub.platform.api.mcp.ContextParam;
import io.casehub.platform.api.mcp.HttpMethod;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PathParam;
import io.casehub.platform.api.mcp.PlatformMutation;
import io.casehub.platform.api.mcp.PlatformQuery;
import io.casehub.platform.api.mcp.RestMethod;
import io.casehub.platform.api.mcp.RestPath;
import io.casehub.platform.api.mcp.RestStatus;
import jakarta.annotation.security.RolesAllowed;

import java.util.List;
import java.util.UUID;

@McpDomain(value = "clinical/trials", app = "clinical", basePath = "/trials")
public interface ClinicalTrialApi {

    @PlatformQuery("List clinical trials")
    @RestPath("/")
    @RolesAllowed({ClinicalGroups.SPONSOR, ClinicalGroups.INVESTIGATOR,
                   ClinicalGroups.COORDINATOR, ClinicalGroups.MONITOR})
    List<TrialListView> listTrials(@ContextParam("tenancyId") String tenancyId);

    @PlatformMutation("Register a new clinical trial")
    @RestPath("/")
    @RestStatus(201)
    @RolesAllowed(ClinicalGroups.SPONSOR)
    TrialView registerTrial(RegisterTrialRequest request,
                            @ContextParam("tenancyId") String tenancyId);

    @PlatformQuery("Get a clinical trial by ID")
    @RestPath("/{id}")
    @RolesAllowed({ClinicalGroups.SPONSOR, ClinicalGroups.INVESTIGATOR,
                   ClinicalGroups.COORDINATOR, ClinicalGroups.MONITOR})
    TrialView getTrial(@PathParam UUID id,
                       @ContextParam("tenancyId") String tenancyId);

    @PlatformMutation("Update sponsor notification configuration")
    @RestPath("/{id}/sponsor-config")
    @RestMethod(HttpMethod.PATCH)
    @RolesAllowed(ClinicalGroups.SPONSOR)
    void updateSponsorConfig(@PathParam UUID id, SponsorConfigRequest request,
                             @ContextParam("tenancyId") String tenancyId);

    @PlatformMutation("Activate a clinical trial")
    @RestPath("/{id}/activate")
    @RolesAllowed(ClinicalGroups.SPONSOR)
    void activateTrial(@PathParam UUID id,
                       @ContextParam("tenancyId") String tenancyId);
}
