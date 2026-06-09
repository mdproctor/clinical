package io.casehub.clinical.api;

import io.casehub.clinical.api.model.DeviationSeverity;
import io.casehub.clinical.api.model.EscalationRequirement;
import io.casehub.clinical.api.model.PiApprovalStatus;
import io.casehub.clinical.api.model.TrialPhase;
import io.casehub.clinical.api.spi.DeviationContext;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ClinicalConstantsTest {

    @Test
    void capability_constants_are_kebab_case() {
        assertThat(ClinicalCapabilities.ELIGIBILITY_SCREENING).isEqualTo("eligibility-screening");
        assertThat(ClinicalCapabilities.SAFETY_MONITORING).isEqualTo("safety-monitoring");
        assertThat(ClinicalCapabilities.PROTOCOL_REVIEW).isEqualTo("protocol-review");
        assertThat(ClinicalCapabilities.IRB_CONSULTATION).isEqualTo("irb-consultation");
        assertThat(ClinicalCapabilities.PI_AUTHORISATION).isEqualTo("pi-authorisation");
        assertThat(ClinicalCapabilities.DATA_SAFETY_MONITORING).isEqualTo("data-safety-monitoring");
        assertThat(ClinicalCapabilities.REGULATORY_SUBMISSION).isEqualTo("regulatory-submission");
        assertThat(ClinicalCapabilities.TRIAL_SUPERVISOR).isEqualTo("trial-supervisor");
    }

    @Test
    void trust_dimension_constants_are_kebab_case() {
        assertThat(ClinicalTrustDimensions.SAFETY_ACCURACY).isEqualTo("safety-accuracy");
        assertThat(ClinicalTrustDimensions.ELIGIBILITY_PRECISION).isEqualTo("eligibility-precision");
        assertThat(ClinicalTrustDimensions.PROTOCOL_ADHERENCE).isEqualTo("protocol-adherence");
    }

    @Test
    void piApprovalStatusHasAllSixValues() {
        var values = Set.of(PiApprovalStatus.values());
        assertThat(values).containsExactlyInAnyOrder(
            PiApprovalStatus.PENDING,
            PiApprovalStatus.COMMANDED,
            PiApprovalStatus.APPROVED,
            PiApprovalStatus.REJECTED,
            PiApprovalStatus.EXPIRED,
            PiApprovalStatus.ESCALATED
        );
    }

    @Test
    void escalationRequirementHasAllValues() {
        var values = Set.of(EscalationRequirement.values());
        assertThat(values).containsExactlyInAnyOrder(
            EscalationRequirement.NONE,
            EscalationRequirement.SPONSOR_NOTIFICATION,
            EscalationRequirement.IRB_REVIEW
        );
    }

    @Test
    void deviationContextCarriesAllFields() {
        var ctx = new DeviationContext(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            "PROT-001", TrialPhase.PHASE_II, DeviationSeverity.MAJOR,
            "sample-window"
        );
        assertThat(ctx.severity()).isEqualTo(DeviationSeverity.MAJOR);
        assertThat(ctx.phase()).isEqualTo(TrialPhase.PHASE_II);
    }

    @Test
    void protocolDeviationResolvedEventCarriesEscalation() {
        var event = new ProtocolDeviationResolvedEvent(
            UUID.randomUUID(), UUID.randomUUID(),
            DeviationSeverity.CRITICAL, EscalationRequirement.IRB_REVIEW,
            PiApprovalStatus.ESCALATED, "DOSING", "pi-001", "test-tenant"
        );
        assertThat(event.escalationRequirement()).isEqualTo(EscalationRequirement.IRB_REVIEW);
        assertThat(event.terminalStatus()).isEqualTo(PiApprovalStatus.ESCALATED);
    }
}
