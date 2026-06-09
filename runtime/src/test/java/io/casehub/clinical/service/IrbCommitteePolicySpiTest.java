package io.casehub.clinical.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.casehub.clinical.api.ProtocolDeviationResolvedEvent;
import io.casehub.clinical.api.model.DeviationSeverity;
import io.casehub.clinical.api.model.EscalationRequirement;
import io.casehub.clinical.api.model.PiApprovalStatus;
import io.casehub.clinical.api.spi.IrbCommitteeAssignment;
import io.casehub.clinical.api.spi.IrbCommitteeAssignmentPolicy;
import io.casehub.clinical.entity.IrbApproval;
import io.casehub.clinical.entity.ProtocolDeviation;
import io.casehub.clinical.entity.TrialSite;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies that IrbDeviationCaseService delegates committee assignment to the
 * IrbCommitteeAssignmentPolicy SPI and that a mock implementation overrides the default.
 *
 * <p>Uses @InjectMock instead of @TestProfile + @Alternative — the profile approach
 * caused getEnabledAlternatives() to replace quarkus.arc.selected-alternatives globally,
 * deactivating MemoryPlanItemStore and breaking other test CDI wiring (clinical#55).
 */
@QuarkusTest
class IrbCommitteePolicySpiTest {

    static final String TEST_COMMITTEE_ID = "test-irb-committee-xyz";

    @InjectMock IrbCommitteeAssignmentPolicy committeePolicy;
    @Inject IrbDeviationCaseService irbDeviationCaseService;

    private UUID deviationId;
    private UUID siteId;
    private UUID trialId;

    @BeforeEach
    @Transactional
    void setup() {
        deviationId = UUID.randomUUID();
        siteId = UUID.randomUUID();
        trialId = UUID.randomUUID();

        when(committeePolicy.evaluate(any())).thenReturn(
            new IrbCommitteeAssignment(TEST_COMMITTEE_ID, List.of(TEST_COMMITTEE_ID)));

        TrialSite site = new TrialSite();
        site.id = siteId;
        site.trialId = trialId;
        site.investigatorId = "test-pi";
        site.persist();

        ProtocolDeviation deviation = new ProtocolDeviation();
        deviation.id = deviationId;
        deviation.siteId = siteId;
        deviation.deviationType = "CONSENT_DEVIATION";
        deviation.severity = DeviationSeverity.CRITICAL;
        deviation.piApprovalStatus = PiApprovalStatus.APPROVED;
        deviation.persist();
    }

    @Test
    void irb_approval_reflects_alternative_policy_committee_id() {
        irbDeviationCaseService.onDeviationResolved(criticalDeviationApproved());

        IrbApproval approval = findApproval(deviationId);
        assertThat(approval).isNotNull();
        assertThat(approval.committeeId).isEqualTo(TEST_COMMITTEE_ID);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    @Transactional
    IrbApproval findApproval(UUID forDeviationId) {
        return IrbApproval.find("deviationId = ?1", forDeviationId).firstResult();
    }

    private ProtocolDeviationResolvedEvent criticalDeviationApproved() {
        return new ProtocolDeviationResolvedEvent(
                deviationId, siteId, DeviationSeverity.CRITICAL,
                EscalationRequirement.IRB_REVIEW, PiApprovalStatus.APPROVED,
                "CONSENT_DEVIATION", "pi-001", "test-tenant");
    }
}
