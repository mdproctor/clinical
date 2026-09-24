package io.casehub.clinical.api.spi;

import io.casehub.clinical.api.ClinicalGroups;
import io.casehub.clinical.api.view.ScheduleVisitRequest;
import io.casehub.clinical.api.view.UpdateVisitRequest;
import io.casehub.clinical.api.view.VisitView;
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

@McpDomain(value = "clinical/visits", app = "clinical", basePath = "/trials/{trialId}/sites/{siteId}/patients/{enrollmentId}/visits", summary = "Clinical trial visit scheduling and recording")
public interface ClinicalVisitApi {

    @PlatformMutation("Schedule a patient visit")
    @RestPath("/")
    @RestStatus(201)
    @RolesAllowed({ClinicalGroups.INVESTIGATOR, ClinicalGroups.COORDINATOR})
    VisitView createVisit(@PathParam UUID trialId, @PathParam UUID siteId,
                          @PathParam UUID enrollmentId, ScheduleVisitRequest request,
                          @ContextParam("tenancyId") String tenancyId);

    @PlatformQuery("List visits for a patient")
    @RestPath("/")
    @RolesAllowed({ClinicalGroups.SPONSOR, ClinicalGroups.INVESTIGATOR,
                   ClinicalGroups.COORDINATOR, ClinicalGroups.MONITOR})
    List<VisitView> listVisits(@PathParam UUID trialId, @PathParam UUID siteId,
                               @PathParam UUID enrollmentId,
                               @ContextParam("tenancyId") String tenancyId);

    @PlatformQuery("Get a visit by ID")
    @RestPath("/{visitId}")
    @RolesAllowed({ClinicalGroups.SPONSOR, ClinicalGroups.INVESTIGATOR,
                   ClinicalGroups.COORDINATOR, ClinicalGroups.MONITOR})
    VisitView getVisit(@PathParam UUID trialId, @PathParam UUID siteId,
                       @PathParam UUID enrollmentId, @PathParam UUID visitId,
                       @ContextParam("tenancyId") String tenancyId);

    @PlatformMutation("Update a visit")
    @RestPath("/{visitId}")
    @RestMethod(HttpMethod.PATCH)
    @RolesAllowed({ClinicalGroups.INVESTIGATOR, ClinicalGroups.COORDINATOR})
    VisitView updateVisit(@PathParam UUID trialId, @PathParam UUID siteId,
                          @PathParam UUID enrollmentId, @PathParam UUID visitId,
                          UpdateVisitRequest request,
                          @ContextParam("tenancyId") String tenancyId);
}
