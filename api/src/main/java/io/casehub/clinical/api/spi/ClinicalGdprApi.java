package io.casehub.clinical.api.spi;

import io.casehub.clinical.api.ClinicalGroups;
import io.casehub.clinical.api.view.EraseResult;
import io.casehub.platform.api.mcp.ContextParam;
import io.casehub.platform.api.mcp.HttpMethod;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PathParam;
import io.casehub.platform.api.mcp.PlatformMutation;
import io.casehub.platform.api.mcp.RestMethod;
import io.casehub.platform.api.mcp.RestPath;
import jakarta.annotation.security.RolesAllowed;

@McpDomain(value = "clinical/gdpr", app = "clinical", basePath = "/api/gdpr/erasure")
public interface ClinicalGdprApi {

    @PlatformMutation("Erase patient data (GDPR Art.17)")
    @RestPath("/patients/{patientId}")
    @RestMethod(HttpMethod.DELETE)
    @RolesAllowed({ClinicalGroups.SPONSOR, ClinicalGroups.COORDINATOR})
    EraseResult erasePatient(@PathParam String patientId,
                             @ContextParam("tenancyId") String tenancyId);
}
