package io.casehub.clinical.service;

import io.casehub.clinical.api.model.DeviationSeverity;
import io.casehub.clinical.api.model.EscalationRequirement;
import io.casehub.clinical.api.model.PiApprovalStatus;
import io.casehub.clinical.entity.ProtocolDeviation;
import io.casehub.clinical.memory.ClinicalMemoryService;
import io.casehub.qhorus.api.message.MessageType;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;

/**
 * Verifies that PiResponseListener writes PI decision facts to ClinicalMemoryService
 * for both DONE (approved) and DECLINE (rejected) paths.
 */
@QuarkusTest
class PiResponseListenerMemoryTest {

    @Inject PiResponseListener listener;
    @InjectMock ClinicalMemoryService memoryService;
    @InjectMock DeviationLedgerWriter ledgerWriter;

    private UUID deviationId;
    private UUID siteId;

    @BeforeEach
    void stubDefaults() {
        doNothing().when(memoryService).storePiDecision(any(), any(), any(), any(), any());
    }

    @BeforeEach
    @Transactional
    void setup() {
        deviationId = UUID.randomUUID();
        siteId = UUID.randomUUID();

        ProtocolDeviation deviation = new ProtocolDeviation();
        deviation.id = deviationId;
        deviation.tenantId = "test-tenant";
        deviation.siteId = siteId;
        deviation.deviationType = "CONSENT_VIOLATION";
        deviation.severity = DeviationSeverity.MAJOR;
        deviation.piApprovalStatus = PiApprovalStatus.COMMANDED;
        deviation.escalationRequirement = EscalationRequirement.NONE;
        deviation.persist();
    }

    @Test
    @Transactional
    void done_stores_approved_pi_decision() {
        listener.process(channelName(), MessageType.DONE, "pi-001");

        ArgumentCaptor<UUID> devCaptor    = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<PiApprovalStatus> statusCaptor = ArgumentCaptor.forClass(PiApprovalStatus.class);
        ArgumentCaptor<String> tenantCaptor = ArgumentCaptor.forClass(String.class);

        verify(memoryService).storePiDecision(devCaptor.capture(), any(),
            any(), statusCaptor.capture(), tenantCaptor.capture());

        assertThat(devCaptor.getValue()).isEqualTo(deviationId);
        assertThat(statusCaptor.getValue()).isEqualTo(PiApprovalStatus.APPROVED);
        assertThat(tenantCaptor.getValue()).isEqualTo("test-tenant");
    }

    @Test
    @Transactional
    void decline_stores_rejected_pi_decision() {
        listener.process(channelName(), MessageType.DECLINE, "pi-001");

        ArgumentCaptor<PiApprovalStatus> statusCaptor = ArgumentCaptor.forClass(PiApprovalStatus.class);
        verify(memoryService).storePiDecision(any(), any(), any(), statusCaptor.capture(), any());
        assertThat(statusCaptor.getValue()).isEqualTo(PiApprovalStatus.REJECTED);
    }

    @Test
    @Transactional
    void done_with_irb_escalation_stores_escalated_pi_decision() {
        // Create a second deviation with IRB escalation so status lands at ESCALATED
        UUID escalatedDevId = UUID.randomUUID();
        ProtocolDeviation escalated = new ProtocolDeviation();
        escalated.id = escalatedDevId;
        escalated.tenantId = "test-tenant";
        escalated.siteId = siteId;
        escalated.deviationType = "CRITICAL_DEVIATION";
        escalated.severity = DeviationSeverity.CRITICAL;
        escalated.piApprovalStatus = PiApprovalStatus.COMMANDED;
        escalated.escalationRequirement = EscalationRequirement.IRB_REVIEW;
        escalated.persist();

        String channel = "clinical/deviation/dev-" + escalatedDevId + "/pi-oversight";
        listener.process(channel, MessageType.DONE, "pi-001");

        ArgumentCaptor<PiApprovalStatus> statusCaptor = ArgumentCaptor.forClass(PiApprovalStatus.class);
        verify(memoryService).storePiDecision(any(), any(), any(), statusCaptor.capture(), any());
        assertThat(statusCaptor.getValue()).isEqualTo(PiApprovalStatus.ESCALATED);
    }

    // ── helper ────────────────────────────────────────────────────────────────

    private String channelName() {
        return "clinical/deviation/dev-" + deviationId + "/pi-oversight";
    }
}
