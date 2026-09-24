package io.casehub.clinical.api.spi;

import io.casehub.clinical.api.ClinicalGroups;
import io.casehub.clinical.api.model.AmendmentPrecedentSearchResponse;
import io.casehub.clinical.api.view.AmendmentView;
import io.casehub.clinical.api.view.ProposeAmendmentRequest;
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

@McpDomain(value = "clinical/amendments", app = "clinical", basePath = "/trials/{trialId}/amendments", summary = "Protocol amendment management")
public interface ClinicalAmendmentApi {

    @PlatformQuery("List protocol amendments for a trial")
    @RestPath("/")
    @RolesAllowed({ClinicalGroups.SPONSOR, ClinicalGroups.INVESTIGATOR,
                   ClinicalGroups.COORDINATOR, ClinicalGroups.MONITOR})
    List<AmendmentView> listAmendments(@PathParam UUID trialId,
                                        @ContextParam("tenancyId") String tenancyId);

    @PlatformMutation("Propose a protocol amendment")
    @RestPath("/")
    @RestStatus(201)
    @RolesAllowed({ClinicalGroups.SPONSOR, ClinicalGroups.INVESTIGATOR})
    AmendmentView proposeAmendment(@PathParam UUID trialId,
                                    ProposeAmendmentRequest request,
                                    @ContextParam("tenancyId") String tenancyId);

    @PlatformQuery("Get a protocol amendment by ID")
    @RestPath("/{amendmentId}")
    @RolesAllowed({ClinicalGroups.SPONSOR, ClinicalGroups.INVESTIGATOR,
                   ClinicalGroups.COORDINATOR, ClinicalGroups.MONITOR})
    AmendmentView getAmendment(@PathParam UUID trialId, @PathParam UUID amendmentId,
                                @ContextParam("tenancyId") String tenancyId);

    @PlatformQuery("Get CBR precedents for a protocol amendment")
    @RestPath("/{amendmentId}/precedents")
    @RolesAllowed({ClinicalGroups.SPONSOR, ClinicalGroups.INVESTIGATOR,
                   ClinicalGroups.COORDINATOR, ClinicalGroups.MONITOR})
    AmendmentPrecedentSearchResponse getAmendmentPrecedents(
            @PathParam UUID trialId, @PathParam UUID amendmentId,
            @ContextParam("tenancyId") String tenancyId,
            @ContextParam("actorId") String actorId);
}
