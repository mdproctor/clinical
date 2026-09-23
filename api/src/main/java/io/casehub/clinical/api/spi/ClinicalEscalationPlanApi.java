package io.casehub.clinical.api.spi;

import io.casehub.clinical.api.ClinicalGroups;
import io.casehub.clinical.api.view.EscalationPlanView;
import io.casehub.platform.api.mcp.ContextParam;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PathParam;
import io.casehub.platform.api.mcp.PlatformQuery;
import io.casehub.platform.api.mcp.RestPath;
import jakarta.annotation.security.RolesAllowed;

import java.util.UUID;

@McpDomain(value = "clinical/escalation-plans", app = "clinical", basePath = "/api/adverse-events")
public interface ClinicalEscalationPlanApi {

    @PlatformQuery("Get escalation plan recommendations for an adverse event")
    @RestPath("/{aeId}/escalation-plans")
    @RolesAllowed({ClinicalGroups.SPONSOR, ClinicalGroups.INVESTIGATOR,
                   ClinicalGroups.COORDINATOR, ClinicalGroups.MONITOR})
    EscalationPlanView getEscalationPlans(@PathParam UUID aeId,
                                           @ContextParam("tenancyId") String tenancyId);
}
