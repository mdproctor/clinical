package io.casehub.clinical.service;

import io.casehub.clinical.api.model.CtcaeGrade;
import io.casehub.clinical.api.spi.AdverseEventContext;
import io.casehub.clinical.api.spi.AdverseEventEscalationRequirements;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultAdverseEventEscalationPolicyTest {

    private final DefaultAdverseEventEscalationPolicy policy = new DefaultAdverseEventEscalationPolicy();

    @ParameterizedTest
    @EnumSource(value = CtcaeGrade.class, names = {"GRADE_1", "GRADE_2"})
    void grade1and2_useDirect_safetyCandidateGroup(CtcaeGrade grade) {
        var ctx = new AdverseEventContext(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), grade);
        AdverseEventEscalationRequirements result = policy.evaluate(ctx);

        assertThat(result.engineCaseRequired()).isFalse();
        assertThat(result.candidateGroups()).isEqualTo("safety-officers");
        assertThat(result.requiresSeniorMonitor()).isFalse();
        assertThat(result.requiresDsmbEscalation()).isFalse();
    }

    @Test
    void grade3_requiresSeniorMonitor_noDsmb() {
        var ctx = new AdverseEventContext(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), CtcaeGrade.GRADE_3);
        AdverseEventEscalationRequirements result = policy.evaluate(ctx);

        assertThat(result.engineCaseRequired()).isTrue();
        assertThat(result.candidateGroups()).isNull();
        assertThat(result.requiresSeniorMonitor()).isTrue();
        assertThat(result.requiresDsmbEscalation()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = CtcaeGrade.class, names = {"GRADE_4", "GRADE_5"})
    void grade4and5_requiresSeniorMonitorAndDsmb(CtcaeGrade grade) {
        var ctx = new AdverseEventContext(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), grade);
        AdverseEventEscalationRequirements result = policy.evaluate(ctx);

        assertThat(result.engineCaseRequired()).isTrue();
        assertThat(result.requiresSeniorMonitor()).isTrue();
        assertThat(result.requiresDsmbEscalation()).isTrue();
    }
}
