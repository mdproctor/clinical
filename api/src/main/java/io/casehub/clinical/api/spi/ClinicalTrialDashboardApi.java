package io.casehub.clinical.api.spi;

import io.casehub.clinical.api.ClinicalGroups;
import io.casehub.clinical.api.model.AePrecedentSearchResponse;
import io.casehub.clinical.api.model.DeviationPrecedentSearchResponse;
import io.casehub.clinical.api.view.AeTrajectoryMatchView;
import io.casehub.clinical.api.view.AeTrajectoryView;
import io.casehub.clinical.api.view.AgentTrustView;
import io.casehub.clinical.api.view.DashboardAdverseEventView;
import io.casehub.clinical.api.view.DashboardDeviationView;
import io.casehub.clinical.api.view.DashboardPatientView;
import io.casehub.clinical.api.view.DashboardSiteView;
import io.casehub.clinical.api.view.GovernanceContextView;
import io.casehub.clinical.api.view.LedgerEntryView;
import io.casehub.clinical.api.view.SiteEnrollmentTrajectoryView;
import io.casehub.clinical.api.view.TrialSummaryView;
import io.casehub.platform.api.mcp.ContextParam;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PathParam;
import io.casehub.platform.api.mcp.PlatformQuery;

import io.casehub.platform.api.mcp.RestPath;
import jakarta.annotation.security.RolesAllowed;

import java.util.List;
import java.util.UUID;

@McpDomain(value = "clinical/trial-dashboard", app = "clinical", basePath = "/trials", summary = "Clinical trial analytics and dashboard metrics")
public interface ClinicalTrialDashboardApi {

    @PlatformQuery("Get trial summary with enrollment, AE, and deviation counts")
    @RestPath("/{trialId}/summary")
    @RolesAllowed({ClinicalGroups.SPONSOR, ClinicalGroups.INVESTIGATOR,
                   ClinicalGroups.COORDINATOR, ClinicalGroups.MONITOR})
    TrialSummaryView summary(@PathParam UUID trialId,
                              @ContextParam("tenancyId") String tenancyId);

    @PlatformQuery("List patients enrolled across all trial sites")
    @RestPath("/{trialId}/patients")
    @RolesAllowed({ClinicalGroups.SPONSOR, ClinicalGroups.INVESTIGATOR,
                   ClinicalGroups.COORDINATOR, ClinicalGroups.MONITOR})
    List<DashboardPatientView> patients(@PathParam UUID trialId,
                                        @ContextParam("tenancyId") String tenancyId);

    @PlatformQuery("List adverse events across all trial sites")
    @RestPath("/{trialId}/adverse-events")
    @RolesAllowed({ClinicalGroups.SPONSOR, ClinicalGroups.INVESTIGATOR,
                   ClinicalGroups.COORDINATOR, ClinicalGroups.MONITOR})
    List<DashboardAdverseEventView> adverseEvents(@PathParam UUID trialId,
                                                   @ContextParam("tenancyId") String tenancyId);

    @PlatformQuery("List protocol deviations across all trial sites")
    @RestPath("/{trialId}/deviations")
    @RolesAllowed({ClinicalGroups.SPONSOR, ClinicalGroups.INVESTIGATOR,
                   ClinicalGroups.COORDINATOR, ClinicalGroups.MONITOR})
    List<DashboardDeviationView> deviations(@PathParam UUID trialId,
                                             @ContextParam("tenancyId") String tenancyId);

    @PlatformQuery("Get agent trust scores for all clinical capabilities")
    @RestPath("/{trialId}/agents")
    @RolesAllowed({ClinicalGroups.SPONSOR, ClinicalGroups.INVESTIGATOR,
                   ClinicalGroups.COORDINATOR, ClinicalGroups.MONITOR})
    List<AgentTrustView> agents(@PathParam UUID trialId,
                                @ContextParam("tenancyId") String tenancyId);

    @PlatformQuery("Get governance context for an adverse event's SUSAR oversight")
    @RestPath("/{trialId}/adverse-events/{aeId}/governance")
    @RolesAllowed({ClinicalGroups.SPONSOR, ClinicalGroups.INVESTIGATOR,
                   ClinicalGroups.COORDINATOR, ClinicalGroups.MONITOR})
    GovernanceContextView governance(@PathParam UUID trialId, @PathParam UUID aeId,
                                      @ContextParam("tenancyId") String tenancyId);

    @PlatformQuery("List ledger entries for a trial, optionally filtered by type")
    @RestPath("/{trialId}/ledger-entries")
    @RolesAllowed({ClinicalGroups.SPONSOR, ClinicalGroups.INVESTIGATOR,
                   ClinicalGroups.COORDINATOR, ClinicalGroups.MONITOR})
    List<LedgerEntryView> ledgerEntries(@PathParam UUID trialId, String typeFilter,
                                         @ContextParam("tenancyId") String tenancyId);

    @PlatformQuery("List trial sites with enrollment and event counts")
    @RestPath("/{trialId}/sites")
    @RolesAllowed({ClinicalGroups.SPONSOR, ClinicalGroups.INVESTIGATOR,
                   ClinicalGroups.COORDINATOR, ClinicalGroups.MONITOR})
    List<DashboardSiteView> sites(@PathParam UUID trialId,
                                   @ContextParam("tenancyId") String tenancyId);

    @PlatformQuery("Find similar adverse event precedents via CBR retrieval")
    @RestPath("/{trialId}/adverse-events/{aeId}/precedents")
    @RolesAllowed({ClinicalGroups.SPONSOR, ClinicalGroups.INVESTIGATOR,
                   ClinicalGroups.COORDINATOR, ClinicalGroups.MONITOR})
    AePrecedentSearchResponse aePrecedents(@PathParam UUID trialId, @PathParam UUID aeId,
                                            @ContextParam("tenancyId") String tenancyId,
                                            @ContextParam("actorId") String actorId);

    @PlatformQuery("Find similar deviation precedents via CBR retrieval")
    @RestPath("/{trialId}/deviations/{devId}/precedents")
    @RolesAllowed({ClinicalGroups.SPONSOR, ClinicalGroups.INVESTIGATOR,
                   ClinicalGroups.COORDINATOR, ClinicalGroups.MONITOR})
    DeviationPrecedentSearchResponse deviationPrecedents(@PathParam UUID trialId,
                                                          @PathParam UUID devId,
                                                          @ContextParam("tenancyId") String tenancyId,
                                                          @ContextParam("actorId") String actorId);

    @PlatformQuery("Get commitment lifecycle for a deviation")
    @RestPath("/{trialId}/deviations/{devId}/commitment")
    @RolesAllowed({ClinicalGroups.SPONSOR, ClinicalGroups.INVESTIGATOR, ClinicalGroups.COORDINATOR})
    Object getCommitmentLifecycle(@PathParam UUID trialId, @PathParam UUID devId,
                                   @ContextParam("tenancyId") String tenancyId);

    @PlatformQuery("Get adverse event escalation trajectory")
    @RestPath("/{trialId}/adverse-events/{aeId}/trajectory")
    @RolesAllowed({ClinicalGroups.SPONSOR, ClinicalGroups.INVESTIGATOR,
                   ClinicalGroups.COORDINATOR, ClinicalGroups.MONITOR})
    AeTrajectoryView aeTrajectory(@PathParam UUID trialId, @PathParam UUID aeId,
                                    @ContextParam("tenancyId") String tenancyId);

    @PlatformQuery("Find matching adverse event trajectory precedents")
    @RestPath("/{trialId}/adverse-events/{aeId}/trajectory/matches")
    @RolesAllowed({ClinicalGroups.SPONSOR, ClinicalGroups.INVESTIGATOR,
                   ClinicalGroups.COORDINATOR, ClinicalGroups.MONITOR})
    AeTrajectoryMatchView aeTrajectoryMatches(@PathParam UUID trialId, @PathParam UUID aeId,
                                               Integer limit, Double minScore,
                                               @ContextParam("tenancyId") String tenancyId);

    @PlatformQuery("Get site enrollment trajectory with trend analysis")
    @RestPath("/{trialId}/sites/{siteId}/enrollment-trajectory")
    @RolesAllowed({ClinicalGroups.SPONSOR, ClinicalGroups.INVESTIGATOR,
                   ClinicalGroups.COORDINATOR, ClinicalGroups.MONITOR})
    SiteEnrollmentTrajectoryView siteEnrollmentTrajectory(@PathParam UUID trialId,
                                                           @PathParam UUID siteId,
                                                           @ContextParam("tenancyId") String tenancyId);
}
