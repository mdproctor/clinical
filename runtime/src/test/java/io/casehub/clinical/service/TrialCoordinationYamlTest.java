package io.casehub.clinical.service;

import io.casehub.api.model.Binding;
import io.casehub.api.model.CaseDefinition;
import io.casehub.api.model.ContextChangeTrigger;
import io.casehub.api.model.HumanTaskTarget;
import io.casehub.api.model.converter.CaseDefinitionYamlMapper;
import io.casehub.api.model.evaluator.JQExpressionEvaluator;
import io.casehub.api.model.evaluator.ListEvaluator;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

class TrialCoordinationYamlTest {

    @Test
    void yaml_parses_without_error() {
        assertThatNoException().isThrownBy(this::load);
    }

    @Test
    void namespace_and_name_correct() {
        CaseDefinition def = load();
        assertThat(def.getNamespace()).isEqualTo("clinical");
        assertThat(def.getName()).isEqualTo("trial-coordination");
    }

    @Test
    void dsmb_rollup_binding_uses_context_change_filter_and_human_task() {
        CaseDefinition def = load();

        Binding dsmb = def.getBindings().stream()
                .filter(b -> "dsmb-rollup".equals(b.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("dsmb-rollup binding not found"));

        assertThat(dsmb.getOn()).isInstanceOf(ContextChangeTrigger.class);
        ContextChangeTrigger trigger = (ContextChangeTrigger) dsmb.getOn();
        assertThat(trigger.getFilter()).isInstanceOf(JQExpressionEvaluator.class);
        JQExpressionEvaluator jq = (JQExpressionEvaluator) trigger.getFilter();
        assertThat(jq.expression())
                .isEqualTo("[.grade4Active // {} | to_entries[] | select(.value == true)] | length >= 2");

        assertThat(dsmb.target()).isInstanceOf(HumanTaskTarget.class);
        HumanTaskTarget task = (HumanTaskTarget) dsmb.target();
        assertThat(task.title()).contains("DSMB");
        ListEvaluator groups = task.candidateGroups();
        assertThat(groups).isInstanceOf(ListEvaluator.StaticList.class);
        assertThat(((ListEvaluator.StaticList) groups).values()).contains("dsmb");
        assertThat(task.expiresIn()).isNotNull();
    }

    // ── helper ───────────────────────────────────────────────────────────────

    private CaseDefinition load() {
        try (InputStream is = getClass().getClassLoader()
                .getResourceAsStream("clinical/trial-coordination.yaml")) {
            assertThat(is).as("trial-coordination.yaml must exist on classpath").isNotNull();
            return CaseDefinitionYamlMapper.load(is);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
