package io.casehub.clinical.memory;

import io.casehub.clinical.api.model.CtcaeGrade;
import io.casehub.platform.api.memory.Memory;
import io.casehub.platform.api.memory.MemoryAttributeKeys;

import java.util.List;
import java.util.Map;

public record ClinicalPatientContext(List<Memory> aeHistory) {

    public static ClinicalPatientContext empty() {
        return new ClinicalPatientContext(List.of());
    }

    public boolean hasHistory() {
        return !aeHistory.isEmpty();
    }

    public boolean hasPriorGrade3OrAbove() {
        return aeHistory.stream().anyMatch(m -> {
            String outcome = m.attributes().get(MemoryAttributeKeys.OUTCOME);
            if (outcome == null) return false;
            try {
                CtcaeGrade g = CtcaeGrade.valueOf(outcome);
                return g.ordinal() >= CtcaeGrade.GRADE_3.ordinal();
            } catch (IllegalArgumentException e) {
                return false;
            }
        });
    }

    public boolean hasPriorEscalation() {
        return aeHistory.stream().anyMatch(m -> {
            String outcome = m.attributes().get(MemoryAttributeKeys.OUTCOME);
            return "ESCALATED".equals(outcome) || "DSMB_ESCALATED".equals(outcome);
        });
    }

    public Map<String, Object> toContextMap() {
        List<Map<String, Object>> facts = aeHistory.stream()
            .map(m -> Map.<String, Object>of(
                "outcome", m.attributes().getOrDefault(MemoryAttributeKeys.OUTCOME, ""),
                "createdAt", m.createdAt().toString()))
            .toList();
        return Map.of(
            "hasHistory", hasHistory(),
            "hasPriorGrade3OrAbove", hasPriorGrade3OrAbove(),
            "hasPriorEscalation", hasPriorEscalation(),
            "aeCount", aeHistory.size(),
            "facts", facts);
    }
}
