package io.casehub.clinical.api.spi;

import io.casehub.clinical.api.ClinicalGroups;
import io.casehub.clinical.api.view.MedicationView;
import io.casehub.clinical.api.view.RecordMedicationRequest;
import io.casehub.clinical.api.view.UpdateMedicationRequest;
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

@McpDomain(value = "clinical/medications", app = "clinical", basePath = "/trials/{trialId}/sites/{siteId}/patients/{enrollmentId}/medications", summary = "Patient medication tracking within trials")
public interface ClinicalMedicationApi {

    @PlatformMutation("Record a concomitant medication")
    @RestPath("/")
    @RestStatus(201)
    @RolesAllowed({ClinicalGroups.INVESTIGATOR, ClinicalGroups.COORDINATOR})
    MedicationView recordMedication(@PathParam UUID trialId, @PathParam UUID siteId,
                                     @PathParam UUID enrollmentId, RecordMedicationRequest request,
                                     @ContextParam("tenancyId") String tenancyId);

    @PlatformQuery("List concomitant medications for a patient")
    @RestPath("/")
    @RolesAllowed({ClinicalGroups.SPONSOR, ClinicalGroups.INVESTIGATOR,
                   ClinicalGroups.COORDINATOR, ClinicalGroups.MONITOR})
    List<MedicationView> listMedications(@PathParam UUID trialId, @PathParam UUID siteId,
                                          @PathParam UUID enrollmentId,
                                          @ContextParam("tenancyId") String tenancyId);

    @PlatformQuery("Get a concomitant medication by ID")
    @RestPath("/{medId}")
    @RolesAllowed({ClinicalGroups.SPONSOR, ClinicalGroups.INVESTIGATOR,
                   ClinicalGroups.COORDINATOR, ClinicalGroups.MONITOR})
    MedicationView getMedication(@PathParam UUID trialId, @PathParam UUID siteId,
                                  @PathParam UUID enrollmentId, @PathParam UUID medId,
                                  @ContextParam("tenancyId") String tenancyId);

    @PlatformMutation("Update a concomitant medication")
    @RestPath("/{medId}")
    @RestMethod(HttpMethod.PATCH)
    @RolesAllowed({ClinicalGroups.INVESTIGATOR, ClinicalGroups.COORDINATOR})
    MedicationView updateMedication(@PathParam UUID trialId, @PathParam UUID siteId,
                                     @PathParam UUID enrollmentId, @PathParam UUID medId,
                                     UpdateMedicationRequest request,
                                     @ContextParam("tenancyId") String tenancyId);
}
