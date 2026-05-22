package io.casehub.clinical.service;

import io.casehub.clinical.api.AdverseEventReportedEvent;
import io.casehub.clinical.api.model.CtcaeGrade;
import io.casehub.clinical.api.spi.AdverseEventContext;
import io.casehub.clinical.api.spi.AdverseEventEscalationPolicy;
import io.casehub.clinical.api.spi.AdverseEventEscalationRequirements;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AeEscalationCaseServiceTest {

    @Mock ClinicalAdverseEventCaseHub caseHub;
    @Mock AdverseEventEscalationPolicy policy;
    @InjectMocks AeEscalationCaseService service;

    @Test
    void grade3_starts_case_with_senior_monitor_context() {
        UUID aeId = UUID.randomUUID();
        UUID enrollmentId = UUID.randomUUID();
        UUID siteId = UUID.randomUUID();
        var event = new AdverseEventReportedEvent(aeId, enrollmentId, siteId, CtcaeGrade.GRADE_3, Instant.now());

        when(policy.evaluate(any())).thenReturn(
                AdverseEventEscalationRequirements.engineManaged(true, false));
        when(caseHub.startCase(any())).thenReturn(CompletableFuture.completedFuture(UUID.randomUUID()));

        service.onAdverseEventReported(event);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(caseHub).startCase(captor.capture());
        Map<String, Object> ctx = captor.getValue();

        assertThat(ctx.get("aeId")).isEqualTo(aeId.toString());
        assertThat(ctx.get("grade")).isEqualTo("GRADE_3");
        assertThat(ctx.get("requiresSeniorMonitor")).isEqualTo(true);
        assertThat(ctx.get("requiresDsmbEscalation")).isEqualTo(false);
    }

    @Test
    void grade4_starts_case_with_dsmb_context() {
        var event = new AdverseEventReportedEvent(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), CtcaeGrade.GRADE_4, Instant.now());

        when(policy.evaluate(any())).thenReturn(
                AdverseEventEscalationRequirements.engineManaged(true, true));
        when(caseHub.startCase(any())).thenReturn(CompletableFuture.completedFuture(UUID.randomUUID()));

        service.onAdverseEventReported(event);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(caseHub).startCase(captor.capture());
        assertThat(captor.getValue().get("requiresDsmbEscalation")).isEqualTo(true);
    }
}
