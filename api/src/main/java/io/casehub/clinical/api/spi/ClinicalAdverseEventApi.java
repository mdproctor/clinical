package io.casehub.clinical.api.spi;

import io.casehub.clinical.api.ClinicalGroups;
import io.casehub.clinical.api.view.AdverseEventView;
import io.casehub.clinical.api.view.GradeHistoryEntry;
import io.casehub.clinical.api.view.RegradeRequest;
import io.casehub.clinical.api.view.ReportAdverseEventRequest;
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

@McpDomain(value = "clinical/adverse-events", app = "clinical", basePath = "/trials/{trialId}/sites/{siteId}/patients/{enrollmentId}/adverse-events", summary = "Record and track clinical trial adverse events")
public interface ClinicalAdverseEventApi {

    @PlatformQuery("List adverse events for a patient")
    @RestPath("/")
    @RolesAllowed({ClinicalGroups.SPONSOR, ClinicalGroups.INVESTIGATOR,
                   ClinicalGroups.COORDINATOR, ClinicalGroups.MONITOR})
    List<AdverseEventView> listAdverseEvents(@PathParam UUID trialId, @PathParam UUID siteId,
                                              @PathParam UUID enrollmentId,
                                              @ContextParam("tenancyId") String tenancyId);

    @PlatformMutation("Report an adverse event")
    @RestPath("/")
    @RestStatus(201)
    @RolesAllowed({ClinicalGroups.INVESTIGATOR, ClinicalGroups.COORDINATOR})
    AdverseEventView reportAdverseEvent(@PathParam UUID trialId, @PathParam UUID siteId,
                                         @PathParam UUID enrollmentId,
                                         ReportAdverseEventRequest request,
                                         @ContextParam("tenancyId") String tenancyId);

    @PlatformQuery("Get an adverse event by ID")
    @RestPath("/{aeId}")
    @RolesAllowed({ClinicalGroups.SPONSOR, ClinicalGroups.INVESTIGATOR,
                   ClinicalGroups.COORDINATOR, ClinicalGroups.MONITOR})
    AdverseEventView getAdverseEvent(@PathParam UUID trialId, @PathParam UUID siteId,
                                      @PathParam UUID enrollmentId, @PathParam UUID aeId,
                                      @ContextParam("tenancyId") String tenancyId);

    @PlatformMutation("Regrade an adverse event")
    @RestPath("/{aeId}/regrade")
    @RolesAllowed({ClinicalGroups.INVESTIGATOR, ClinicalGroups.COORDINATOR})
    AdverseEventView regradeAdverseEvent(@PathParam UUID trialId, @PathParam UUID siteId,
                                          @PathParam UUID enrollmentId, @PathParam UUID aeId,
                                          RegradeRequest request,
                                          @ContextParam("tenancyId") String tenancyId,
                                          @ContextParam("actorId") String actorId);

    @PlatformQuery("Get grade change history for an adverse event")
    @RestPath("/{aeId}/grade-history")
    @RolesAllowed({ClinicalGroups.INVESTIGATOR, ClinicalGroups.COORDINATOR})
    List<GradeHistoryEntry> getGradeHistory(@PathParam UUID trialId, @PathParam UUID siteId,
                                             @PathParam UUID enrollmentId, @PathParam UUID aeId,
                                             @ContextParam("tenancyId") String tenancyId);
}
