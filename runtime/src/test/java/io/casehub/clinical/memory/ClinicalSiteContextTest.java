package io.casehub.clinical.memory;

import io.casehub.platform.api.memory.Memory;
import io.casehub.platform.api.memory.MemoryAttributeKeys;
import io.casehub.platform.api.memory.MemoryDomain;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ClinicalSiteContextTest {

    @Test
    void empty_returns_safe_defaults() {
        ClinicalSiteContext ctx = ClinicalSiteContext.empty();

        assertThat(ctx.hasComplianceIssues()).isFalse();
        assertThat(ctx.recentTimelineBreachCount()).isZero();

        Map<String, Object> map = ctx.toContextMap();
        assertThat(map.get("hasComplianceIssues")).isEqualTo(false);
        assertThat(map.get("recentTimelineBreachCount")).isEqualTo(0);
        assertThat((List<?>) map.get("facts")).isEmpty();
    }

    @Test
    void any_event_sets_hasComplianceIssues() {
        ClinicalSiteContext ctx = new ClinicalSiteContext(List.of(memory("APPROVED")));

        assertThat(ctx.hasComplianceIssues()).isTrue();
    }

    @Test
    void timeline_breach_counted() {
        ClinicalSiteContext ctx = new ClinicalSiteContext(List.of(
            memory("TIMELINE_BREACH"),
            memory("TIMELINE_BREACH"),
            memory("APPROVED")
        ));

        assertThat(ctx.recentTimelineBreachCount()).isEqualTo(2);
    }

    @Test
    void non_breach_outcomes_not_counted() {
        ClinicalSiteContext ctx = new ClinicalSiteContext(List.of(
            memory("APPROVED"),
            memory("REJECTED"),
            memory("GRADE_3")
        ));

        assertThat(ctx.recentTimelineBreachCount()).isZero();
    }

    @Test
    void toContextMap_includes_facts() {
        ClinicalSiteContext ctx = new ClinicalSiteContext(List.of(memory("TIMELINE_BREACH")));

        Map<String, Object> map = ctx.toContextMap();
        assertThat(map.get("recentTimelineBreachCount")).isEqualTo(1);
        List<?> facts = (List<?>) map.get("facts");
        assertThat(facts).hasSize(1);
    }

    // -- helper --

    private static Memory memory(String outcome) {
        return new Memory(
            UUID.randomUUID().toString(),
            "site:" + UUID.randomUUID(),
            new MemoryDomain("clinical-site"),
            "test-tenant",
            null,
            "Site compliance event",
            Map.of(MemoryAttributeKeys.OUTCOME, outcome, MemoryAttributeKeys.ACTOR_ID, "clinical-service"),
            Instant.now());
    }
}
