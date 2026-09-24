package io.casehub.clinical.api.spi;

import io.casehub.clinical.api.CascadeEvent;
import io.casehub.clinical.api.ClinicalGroups;
import io.casehub.platform.api.mcp.ContextParam;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PathParam;
import io.casehub.platform.api.mcp.PlatformQuery;
import io.casehub.platform.api.mcp.RestPath;
import jakarta.annotation.security.RolesAllowed;

import java.util.List;
import java.util.UUID;

@McpDomain(value = "clinical/cascade", app = "clinical", basePath = "/api/adverse-events", summary = "Adverse event cascade detection and response")
public interface ClinicalCascadeApi {

    @PlatformQuery("Get the escalation cascade for an adverse event")
    @RestPath("/{aeId}/cascade")
    @RolesAllowed({ClinicalGroups.SPONSOR, ClinicalGroups.INVESTIGATOR,
                   ClinicalGroups.COORDINATOR, ClinicalGroups.MONITOR})
    List<CascadeEvent> getCascade(@PathParam UUID aeId,
                                   @ContextParam("tenancyId") String tenancyId);
}
