package io.casehub.clinical.memory;

import io.casehub.platform.api.memory.Memory;
import io.casehub.platform.api.memory.MemoryAttributeKeys;

import java.util.List;
import java.util.Map;

public record ClinicalSiteContext(List<Memory> complianceEvents) {

    public static ClinicalSiteContext empty() {
        return new ClinicalSiteContext(List.of());
    }

    public boolean hasComplianceIssues() {
        return !complianceEvents.isEmpty();
    }

    public int recentTimelineBreachCount() {
        return (int) complianceEvents.stream()
            .filter(m -> "TIMELINE_BREACH".equals(m.attributes().get(MemoryAttributeKeys.OUTCOME)))
            .count();
    }

    public Map<String, Object> toContextMap() {
        List<Map<String, Object>> facts = complianceEvents.stream()
            .map(m -> Map.<String, Object>of(
                "outcome", m.attributes().getOrDefault(MemoryAttributeKeys.OUTCOME, ""),
                "createdAt", m.createdAt().toString()))
            .toList();
        return Map.of(
            "hasComplianceIssues", hasComplianceIssues(),
            "recentTimelineBreachCount", recentTimelineBreachCount(),
            "facts", facts);
    }
}
