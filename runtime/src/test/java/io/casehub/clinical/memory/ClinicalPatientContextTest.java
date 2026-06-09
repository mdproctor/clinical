package io.casehub.clinical.memory;

import io.casehub.clinical.api.model.CtcaeGrade;
import io.casehub.platform.api.memory.Memory;
import io.casehub.platform.api.memory.MemoryAttributeKeys;
import io.casehub.platform.api.memory.MemoryDomain;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ClinicalPatientContextTest {

    @Test
    void empty_returns_safe_defaults() {
        ClinicalPatientContext ctx = ClinicalPatientContext.empty();

        assertThat(ctx.hasHistory()).isFalse();
        assertThat(ctx.hasPriorGrade3OrAbove()).isFalse();
        assertThat(ctx.hasPriorEscalation()).isFalse();

        Map<String, Object> map = ctx.toContextMap();
        assertThat(map.get("hasHistory")).isEqualTo(false);
        assertThat(map.get("hasPriorGrade3OrAbove")).isEqualTo(false);
        assertThat(map.get("hasPriorEscalation")).isEqualTo(false);
        assertThat(map.get("aeCount")).isEqualTo(0);
        assertThat((List<?>) map.get("facts")).isEmpty();
    }

    @Test
    void grade2_does_not_set_hasPriorGrade3OrAbove() {
        ClinicalPatientContext ctx = contextWith(CtcaeGrade.GRADE_2);

        assertThat(ctx.hasHistory()).isTrue();
        assertThat(ctx.hasPriorGrade3OrAbove()).isFalse();
    }

    @Test
    void grade3_sets_hasPriorGrade3OrAbove() {
        ClinicalPatientContext ctx = contextWith(CtcaeGrade.GRADE_3);

        assertThat(ctx.hasPriorGrade3OrAbove()).isTrue();
    }

    @Test
    void grade4_sets_hasPriorGrade3OrAbove() {
        ClinicalPatientContext ctx = contextWith(CtcaeGrade.GRADE_4);

        assertThat(ctx.hasPriorGrade3OrAbove()).isTrue();
    }

    @Test
    void grade5_sets_hasPriorGrade3OrAbove() {
        ClinicalPatientContext ctx = contextWith(CtcaeGrade.GRADE_5);

        assertThat(ctx.hasPriorGrade3OrAbove()).isTrue();
    }

    @Test
    void escalated_outcome_sets_hasPriorEscalation() {
        ClinicalPatientContext ctx = contextWithOutcome("ESCALATED");

        assertThat(ctx.hasPriorEscalation()).isTrue();
    }

    @Test
    void dsmb_escalated_outcome_sets_hasPriorEscalation() {
        ClinicalPatientContext ctx = contextWithOutcome("DSMB_ESCALATED");

        assertThat(ctx.hasPriorEscalation()).isTrue();
    }

    @Test
    void grade_outcome_does_not_set_hasPriorEscalation() {
        ClinicalPatientContext ctx = contextWithOutcome("GRADE_4");

        assertThat(ctx.hasPriorEscalation()).isFalse();
    }

    @Test
    void toContextMap_includes_aeCount_and_facts() {
        ClinicalPatientContext ctx = contextWith(CtcaeGrade.GRADE_3);

        Map<String, Object> map = ctx.toContextMap();
        assertThat(map.get("aeCount")).isEqualTo(1);
        List<?> facts = (List<?>) map.get("facts");
        assertThat(facts).hasSize(1);
    }

    // -- helpers --

    private static ClinicalPatientContext contextWith(CtcaeGrade grade) {
        return new ClinicalPatientContext(List.of(memory(grade.name())));
    }

    private static ClinicalPatientContext contextWithOutcome(String outcome) {
        return new ClinicalPatientContext(List.of(memory(outcome)));
    }

    private static Memory memory(String outcome) {
        return new Memory(
            UUID.randomUUID().toString(),
            "patient:" + UUID.randomUUID(),
            new MemoryDomain("clinical-patient"),
            "test-tenant",
            null,
            "AE report",
            Map.of(MemoryAttributeKeys.OUTCOME, outcome, MemoryAttributeKeys.ACTOR_ID, "clinical-service"),
            Instant.now());
    }
}
