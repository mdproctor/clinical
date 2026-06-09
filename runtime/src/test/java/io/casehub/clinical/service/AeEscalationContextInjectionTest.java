package io.casehub.clinical.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.clinical.api.AdverseEventReportedEvent;
import io.casehub.clinical.api.model.AeEscalationStatus;
import io.casehub.clinical.api.model.AeOutcome;
import io.casehub.clinical.api.model.CtcaeGrade;
import io.casehub.clinical.api.model.EventActuality;
import io.casehub.clinical.entity.AdverseEvent;
import io.casehub.platform.api.memory.CaseMemoryStore;
import io.casehub.platform.api.memory.MemoryAttributeKeys;
import io.casehub.platform.api.memory.MemoryInput;
import io.casehub.platform.expression.JQEvaluator;
import io.casehub.platform.expression.ValidationResult;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that AeEscalationCaseService enriches the engine's initialContext
 * with patient and site memory when history exists in the store.
 *
 * <p>Seeds InMemoryMemoryStore (the selected @Alternative in tests) with memory
 * entries before calling prepareAndMarkRequested(), then asserts both the map
 * shape and JQ navigation (.patientContext.hasPriorGrade3OrAbove).
 */
@QuarkusTest
class AeEscalationContextInjectionTest {

    /** MockCurrentPrincipal's default tenancyId — used for all store.store() seeds. */
    private static final String TEST_TENANT = "278776f9-e1b0-46fb-9032-8bddebdcf9ce";

    @Inject AeEscalationCaseService service;
    @Inject CaseMemoryStore store;
    @Inject JQEvaluator jqEvaluator;
    @Inject ObjectMapper objectMapper;

    private UUID aeId;
    private UUID enrollmentId;
    private UUID siteId;

    @BeforeEach
    @Transactional
    void setup() {
        aeId = UUID.randomUUID();
        enrollmentId = UUID.randomUUID();
        siteId = UUID.randomUUID();

        // Create AE entity — service loads it in Phase 1
        AdverseEvent ae = new AdverseEvent();
        ae.id = aeId;
        ae.tenantId = TEST_TENANT;
        ae.enrollmentId = enrollmentId;
        ae.grade = CtcaeGrade.GRADE_3;
        ae.actuality = EventActuality.ACTUAL;
        ae.outcome = AeOutcome.ONGOING;
        ae.occurredAt = Instant.now();
        ae.reportedAt = Instant.now();
        ae.slaDeadline = Instant.now().plusSeconds(86400);
        ae.escalationStatus = AeEscalationStatus.NONE;
        ae.persist();
    }

    @Test
    void initialContext_contains_patientContext_from_seeded_store() throws Exception {
        // Seed a Grade 3 AE report in the PATIENT domain
        store.store(new MemoryInput(
            "patient:" + enrollmentId,
            io.casehub.clinical.memory.ClinicalMemoryDomains.PATIENT,
            TEST_TENANT, null,
            "Grade 3 AE report",
            Map.of(MemoryAttributeKeys.ACTOR_ID, "clinical-service",
                   MemoryAttributeKeys.OUTCOME, "GRADE_3")));

        var event = new AdverseEventReportedEvent(aeId, enrollmentId, siteId,
            CtcaeGrade.GRADE_3, Instant.now(), TEST_TENANT);

        Map<String, Object> ctx = service.prepareAndMarkRequested(event);

        assertThat(ctx).containsKey("patientContext");
        assertThat(ctx).containsKey("siteContext");

        @SuppressWarnings("unchecked")
        Map<String, Object> patientCtx = (Map<String, Object>) ctx.get("patientContext");
        assertThat(patientCtx.get("hasHistory")).isEqualTo(true);
        assertThat(patientCtx.get("hasPriorGrade3OrAbove")).isEqualTo(true);
        assertThat(patientCtx.get("hasPriorEscalation")).isEqualTo(false);
        assertThat(patientCtx.get("aeCount")).isEqualTo(1);
    }

    @Test
    void patientContext_jq_navigation_resolves_hasPriorGrade3OrAbove() throws Exception {
        // Seed Grade 4 entry
        store.store(new MemoryInput(
            "patient:" + enrollmentId,
            io.casehub.clinical.memory.ClinicalMemoryDomains.PATIENT,
            TEST_TENANT, null,
            "Grade 4 AE report",
            Map.of(MemoryAttributeKeys.ACTOR_ID, "clinical-service",
                   MemoryAttributeKeys.OUTCOME, "GRADE_4")));

        var event = new AdverseEventReportedEvent(aeId, enrollmentId, siteId,
            CtcaeGrade.GRADE_4, Instant.now(), TEST_TENANT);

        Map<String, Object> ctx = service.prepareAndMarkRequested(event);

        // Convert context to JsonNode for JQ evaluation
        var ctxNode = objectMapper.valueToTree(ctx);

        ValidationResult result = jqEvaluator.eval(".patientContext.hasPriorGrade3OrAbove", ctxNode);
        assertThat(result.ok()).isTrue();
        assertThat(result.isTrue()).isTrue();
    }

    @Test
    @Transactional
    void initialContext_has_empty_contexts_when_store_is_empty() {
        var event = new AdverseEventReportedEvent(aeId, enrollmentId, siteId,
            CtcaeGrade.GRADE_3, Instant.now(), TEST_TENANT);

        Map<String, Object> ctx = service.prepareAndMarkRequested(event);

        @SuppressWarnings("unchecked")
        Map<String, Object> patientCtx = (Map<String, Object>) ctx.get("patientContext");
        assertThat(patientCtx.get("hasHistory")).isEqualTo(false);
        assertThat(patientCtx.get("hasPriorGrade3OrAbove")).isEqualTo(false);
    }
}
