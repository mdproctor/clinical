package io.casehub.clinical.service;

import io.casehub.clinical.api.ProtocolDeviationResolvedEvent;
import io.casehub.clinical.api.model.DeviationSeverity;
import io.casehub.clinical.api.model.EscalationRequirement;
import io.casehub.clinical.api.model.PiApprovalStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IrbDeviationCaseServiceTest {

    @Mock ClinicalDeviationCaseHub caseHub;
    @InjectMocks IrbDeviationCaseService service;

    @Test
    void irb_review_and_approved_starts_case_with_correct_context() {
        UUID deviationId = UUID.randomUUID();
        UUID siteId = UUID.randomUUID();
        var event = new ProtocolDeviationResolvedEvent(
                deviationId, siteId, DeviationSeverity.CRITICAL,
                EscalationRequirement.IRB_REVIEW, PiApprovalStatus.APPROVED,
                "CONSENT_DEVIATION", "pi-001");

        when(caseHub.startCase(any())).thenReturn(CompletableFuture.completedFuture(UUID.randomUUID()));

        service.onDeviationResolved(event);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(caseHub).startCase(captor.capture());

        Map<String, Object> ctx = captor.getValue();
        assertThat(ctx.get("deviationId")).isEqualTo(deviationId.toString());
        assertThat(ctx.get("siteId")).isEqualTo(siteId.toString());
        assertThat(ctx.get("severity")).isEqualTo("CRITICAL");
        assertThat(ctx.get("irbConsultationRequired")).isEqualTo(true);
    }

    @Test
    void non_irb_escalation_does_not_start_case() {
        var event = new ProtocolDeviationResolvedEvent(
                UUID.randomUUID(), UUID.randomUUID(), DeviationSeverity.MAJOR,
                EscalationRequirement.SPONSOR_NOTIFICATION, PiApprovalStatus.APPROVED,
                "PROTOCOL_DEVIATION", "pi-001");

        service.onDeviationResolved(event);

        verifyNoInteractions(caseHub);
    }

    @Test
    void pi_rejected_does_not_start_case() {
        var event = new ProtocolDeviationResolvedEvent(
                UUID.randomUUID(), UUID.randomUUID(), DeviationSeverity.CRITICAL,
                EscalationRequirement.IRB_REVIEW, PiApprovalStatus.REJECTED,
                "CONSENT_DEVIATION", null);

        service.onDeviationResolved(event);

        verifyNoInteractions(caseHub);
    }
}
