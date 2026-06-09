package io.casehub.clinical.memory;

import io.casehub.clinical.api.model.CtcaeGrade;
import io.casehub.clinical.api.model.DeviationSeverity;
import io.casehub.clinical.api.model.PiApprovalStatus;
import io.casehub.platform.api.memory.CaseMemoryStore;
import io.casehub.platform.api.memory.Memory;
import io.casehub.platform.api.memory.MemoryAttributeKeys;
import io.casehub.platform.api.memory.MemoryDomain;
import io.casehub.platform.api.memory.MemoryInput;
import io.casehub.platform.api.memory.MemoryQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClinicalMemoryServiceTest {

    @Mock CaseMemoryStore store;

    ClinicalMemoryService service;

    @BeforeEach
    void setUp() {
        service = new ClinicalMemoryService(store);
    }

    // ── storeAeReport ─────────────────────────────────────────────────────────

    @Test
    void storeAeReport_writes_to_patient_and_site_domains() {
        UUID aeId = UUID.randomUUID();
        UUID enrollmentId = UUID.randomUUID();
        UUID siteId = UUID.randomUUID();

        service.storeAeReport(aeId, enrollmentId, siteId, CtcaeGrade.GRADE_3, "tenant-1");

        ArgumentCaptor<MemoryInput> captor = ArgumentCaptor.forClass(MemoryInput.class);
        verify(store, org.mockito.Mockito.times(2)).store(captor.capture());

        List<MemoryInput> inputs = captor.getAllValues();
        assertThat(inputs).hasSize(2);

        MemoryInput patient = inputs.stream()
            .filter(i -> i.entityId().startsWith("patient:")).findFirst().orElseThrow();
        assertThat(patient.domain()).isEqualTo(ClinicalMemoryDomains.PATIENT);
        assertThat(patient.tenantId()).isEqualTo("tenant-1");
        assertThat(patient.attributes()).containsEntry(MemoryAttributeKeys.OUTCOME, "GRADE_3");
        assertThat(patient.attributes()).containsEntry(MemoryAttributeKeys.ACTOR_ID, "clinical-service");

        MemoryInput site = inputs.stream()
            .filter(i -> i.entityId().startsWith("site:")).findFirst().orElseThrow();
        assertThat(site.domain()).isEqualTo(ClinicalMemoryDomains.SITE);
        assertThat(site.entityId()).isEqualTo("site:" + siteId);
    }

    // ── storeAeOutcome ────────────────────────────────────────────────────────

    @Test
    void storeAeOutcome_escalated_writes_patient_domain() {
        service.storeAeOutcome(UUID.randomUUID(), UUID.randomUUID(), CtcaeGrade.GRADE_3,
            "REVIEWED", false, "tenant-1");

        ArgumentCaptor<MemoryInput> captor = ArgumentCaptor.forClass(MemoryInput.class);
        verify(store).store(captor.capture());

        MemoryInput input = captor.getValue();
        assertThat(input.domain()).isEqualTo(ClinicalMemoryDomains.PATIENT);
        assertThat(input.attributes()).containsEntry(MemoryAttributeKeys.OUTCOME, "ESCALATED");
    }

    @Test
    void storeAeOutcome_dsmb_escalated_sets_dsmb_outcome() {
        service.storeAeOutcome(UUID.randomUUID(), UUID.randomUUID(), CtcaeGrade.GRADE_4,
            "REVIEWED", true, "tenant-1");

        ArgumentCaptor<MemoryInput> captor = ArgumentCaptor.forClass(MemoryInput.class);
        verify(store).store(captor.capture());

        assertThat(captor.getValue().attributes())
            .containsEntry(MemoryAttributeKeys.OUTCOME, "DSMB_ESCALATED");
    }

    // ── storeDeviationReport ──────────────────────────────────────────────────

    @Test
    void storeDeviationReport_writes_site_domain() {
        UUID siteId = UUID.randomUUID();
        service.storeDeviationReport(UUID.randomUUID(), siteId, "CONSENT_VIOLATION",
            DeviationSeverity.MAJOR, "tenant-1");

        ArgumentCaptor<MemoryInput> captor = ArgumentCaptor.forClass(MemoryInput.class);
        verify(store).store(captor.capture());

        MemoryInput input = captor.getValue();
        assertThat(input.domain()).isEqualTo(ClinicalMemoryDomains.SITE);
        assertThat(input.entityId()).isEqualTo("site:" + siteId);
        assertThat(input.attributes()).containsEntry(MemoryAttributeKeys.OUTCOME, "MAJOR");
    }

    // ── storePiDecision ───────────────────────────────────────────────────────

    @Test
    void storePiDecision_approved_writes_approved_outcome() {
        service.storePiDecision(UUID.randomUUID(), UUID.randomUUID(), "CONSENT_VIOLATION",
            PiApprovalStatus.APPROVED, "tenant-1");

        ArgumentCaptor<MemoryInput> captor = ArgumentCaptor.forClass(MemoryInput.class);
        verify(store).store(captor.capture());
        assertThat(captor.getValue().attributes())
            .containsEntry(MemoryAttributeKeys.OUTCOME, "APPROVED");
    }

    @Test
    void storePiDecision_expired_writes_timeline_breach() {
        service.storePiDecision(UUID.randomUUID(), UUID.randomUUID(), "CONSENT_VIOLATION",
            PiApprovalStatus.EXPIRED, "tenant-1");

        ArgumentCaptor<MemoryInput> captor = ArgumentCaptor.forClass(MemoryInput.class);
        verify(store).store(captor.capture());
        assertThat(captor.getValue().attributes())
            .containsEntry(MemoryAttributeKeys.OUTCOME, "TIMELINE_BREACH");
    }

    // ── error swallowing ──────────────────────────────────────────────────────

    @Test
    void store_failure_is_swallowed_and_does_not_propagate() {
        doThrow(new RuntimeException("store unavailable")).when(store).store(any());

        assertThatCode(() ->
            service.storeAeReport(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                CtcaeGrade.GRADE_3, "tenant-1"))
            .doesNotThrowAnyException();
    }

    // ── queryPatientContext ───────────────────────────────────────────────────

    @Test
    void queryPatientContext_returns_populated_context() {
        UUID enrollmentId = UUID.randomUUID();
        Memory m = new Memory(UUID.randomUUID().toString(), "patient:" + enrollmentId,
            ClinicalMemoryDomains.PATIENT, "tenant-1", null, "AE report",
            Map.of(MemoryAttributeKeys.OUTCOME, "GRADE_3", MemoryAttributeKeys.ACTOR_ID, "clinical-service"),
            Instant.now());
        when(store.query(any(MemoryQuery.class))).thenReturn(List.of(m));

        ClinicalPatientContext ctx = service.queryPatientContext(enrollmentId, "tenant-1");

        assertThat(ctx.hasHistory()).isTrue();
        assertThat(ctx.hasPriorGrade3OrAbove()).isTrue();
    }

    @Test
    void queryPatientContext_returns_empty_on_store_failure() {
        when(store.query(any(MemoryQuery.class))).thenThrow(new RuntimeException("store unavailable"));

        ClinicalPatientContext ctx = service.queryPatientContext(UUID.randomUUID(), "tenant-1");

        assertThat(ctx.hasHistory()).isFalse();
    }

    // ── querySiteContext ──────────────────────────────────────────────────────

    @Test
    void querySiteContext_returns_populated_context() {
        UUID siteId = UUID.randomUUID();
        Memory m = new Memory(UUID.randomUUID().toString(), "site:" + siteId,
            ClinicalMemoryDomains.SITE, "tenant-1", null, "EXPIRED deviation",
            Map.of(MemoryAttributeKeys.OUTCOME, "TIMELINE_BREACH", MemoryAttributeKeys.ACTOR_ID, "clinical-service"),
            Instant.now());
        when(store.query(any(MemoryQuery.class))).thenReturn(List.of(m));

        ClinicalSiteContext ctx = service.querySiteContext(siteId, "tenant-1");

        assertThat(ctx.hasComplianceIssues()).isTrue();
        assertThat(ctx.recentTimelineBreachCount()).isEqualTo(1);
    }

    @Test
    void querySiteContext_uses_since_180_days_and_limit_50() {
        service.querySiteContext(UUID.randomUUID(), "tenant-1");

        ArgumentCaptor<MemoryQuery> captor = ArgumentCaptor.forClass(MemoryQuery.class);
        verify(store).query(captor.capture());

        MemoryQuery query = captor.getValue();
        assertThat(query.limit()).isEqualTo(50);
        assertThat(query.since()).isNotNull();
        // since must be approximately now minus 180 days (within 5 seconds tolerance)
        Instant expected180DaysAgo = Instant.now().minus(180, java.time.temporal.ChronoUnit.DAYS);
        assertThat(query.since()).isCloseTo(expected180DaysAgo,
            org.assertj.core.api.Assertions.within(5, java.time.temporal.ChronoUnit.SECONDS));
    }

    @Test
    void querySiteContext_returns_empty_on_store_failure() {
        when(store.query(any(MemoryQuery.class))).thenThrow(new RuntimeException("store unavailable"));

        ClinicalSiteContext ctx = service.querySiteContext(UUID.randomUUID(), "tenant-1");

        assertThat(ctx.hasComplianceIssues()).isFalse();
    }
}
