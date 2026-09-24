package io.casehub.clinical.api.spi;

import io.casehub.blocks.summarisation.narrative.DecisionNarrative;
import io.casehub.clinical.api.ClinicalGroups;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PathParam;
import io.casehub.platform.api.mcp.PlatformQuery;
import jakarta.annotation.security.RolesAllowed;

import java.util.List;

@McpDomain(value = "clinical/narrative", app = "clinical", basePath = "/api/narrative", summary = "Adverse event narrative generation")
public interface ClinicalNarrativeApi {

    @PlatformQuery("Get decision narratives for a case")
    @RolesAllowed({ClinicalGroups.SPONSOR, ClinicalGroups.INVESTIGATOR,
                   ClinicalGroups.COORDINATOR, ClinicalGroups.MONITOR})
    List<DecisionNarrative> get(@PathParam String caseId);
}
