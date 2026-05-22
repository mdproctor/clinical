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

import java.util.UUID;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IrbDeviationCaseServiceTest {

    @Mock ClinicalDeviationCaseHub caseHub;
    @InjectMocks IrbDeviationCaseService service;

    // Happy-path (IRB_REVIEW + APPROVED) tested by IrbGateLifecycleTest (@QuarkusTest)
    // because onDeviationResolved() persists IrbApproval — requires Panache/Quarkus container.

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
