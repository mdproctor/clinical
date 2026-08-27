package io.casehub.clinical.cbr;

import io.casehub.clinical.api.model.AeOutcome;
import io.casehub.clinical.api.model.CtcaeGrade;
import io.casehub.clinical.api.model.EventActuality;
import io.casehub.clinical.api.model.SiteStatus;
import io.casehub.clinical.api.model.TrialPhase;
import io.casehub.clinical.api.model.TrialStatus;
import io.casehub.clinical.entity.AdverseEvent;
import io.casehub.clinical.entity.ClinicalTrial;
import io.casehub.clinical.entity.PatientEnrollment;
import io.casehub.clinical.entity.TrialSafetySignal;
import io.casehub.clinical.entity.TrialSite;
import io.casehub.clinical.support.WorkItemQueries;
import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.work.api.WorkItem;
import io.casehub.work.runtime.service.WorkItemService;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@TestSecurity(user = "test-actor", roles = {"sponsor", "investigator", "coordinator"})
class DsmbBatchSignalWorkItemTest {

    @Inject TrialSafetyAggregationJob aggregationJob;
    @Inject WorkItemQueries workItemQueries;
    @Inject WorkItemService workItemService;
    @Inject CurrentPrincipal principal;

    private UUID trialId;

    @BeforeEach
    void setup() {
        trialId = seedTrialWithGradeThresholdSignal();
    }

    @Test
    void batch_signal_creates_dsmb_workitem() {
        aggregationJob.aggregateTrial(trialId, "PHASE_III");

        TrialSafetySignal signal = findSignal(trialId, "GRADE_THRESHOLD");
        assertThat(signal).as("Signal record created").isNotNull();
        assertThat(signal.workItemId).as("WorkItem ID set on signal").isNotNull();

        List<WorkItem> items = dsmbBatchWorkItems();
        assertThat(items).as("Exactly one DSMB batch WorkItem").hasSize(1);
        assertThat(items.get(0).title()).contains("DSMB review");
        assertThat(items.get(0).title()).contains("GRADE_THRESHOLD");
        assertThat(items.get(0).callerRef()).contains("clinical:trial-safety-signal/");
    }

    @Test
    void idempotent_no_duplicate_workitem_on_second_run() {
        aggregationJob.aggregateTrial(trialId, "PHASE_III");
        UUID firstWorkItemId = findSignal(trialId, "GRADE_THRESHOLD").workItemId;
        assertThat(firstWorkItemId).isNotNull();

        aggregationJob.aggregateTrial(trialId, "PHASE_III");
        UUID secondWorkItemId = findSignal(trialId, "GRADE_THRESHOLD").workItemId;
        assertThat(secondWorkItemId).isEqualTo(firstWorkItemId);
        assertThat(dsmbBatchWorkItems()).hasSize(1);
    }

    @Test
    void creates_new_workitem_after_previous_completed() {
        aggregationJob.aggregateTrial(trialId, "PHASE_III");
        UUID firstWorkItemId = findSignal(trialId, "GRADE_THRESHOLD").workItemId;
        assertThat(firstWorkItemId).isNotNull();

        completeWorkItem(firstWorkItemId);

        aggregationJob.aggregateTrial(trialId, "PHASE_III");
        UUID secondWorkItemId = findSignal(trialId, "GRADE_THRESHOLD").workItemId;
        assertThat(secondWorkItemId).isNotEqualTo(firstWorkItemId);
    }

    // ── helpers ──────────────────────────────────────────────

    @Transactional
    UUID seedTrialWithGradeThresholdSignal() {
        ClinicalTrial trial = new ClinicalTrial();
        trial.id = UUID.randomUUID();
        trial.protocolId = "DSMB-BATCH-TEST-" + UUID.randomUUID().toString().substring(0, 8);
        trial.phase = TrialPhase.PHASE_III;
        trial.sponsor = "TestSponsor";
        trial.targetEnrollment = 100;
        trial.status = TrialStatus.ACTIVE;
        trial.tenantId = "default";
        trial.persist();

        for (int i = 0; i < 4; i++) {
            TrialSite site = new TrialSite();
            site.id = UUID.randomUUID();
            site.trialId = trial.id;
            site.investigatorId = "pi-batch-" + i;
            site.status = SiteStatus.ACTIVE;
            site.tenantId = "default";
            site.persist();

            PatientEnrollment enrollment = new PatientEnrollment();
            enrollment.id = UUID.randomUUID();
            enrollment.siteId = site.id;
            enrollment.patientId = "patient-batch-" + i + "-" + UUID.randomUUID().toString().substring(0, 4);
            enrollment.tenantId = "default";
            enrollment.persist();

            AdverseEvent ae1 = new AdverseEvent();
            ae1.id = UUID.randomUUID();
            ae1.enrollmentId = enrollment.id;
            ae1.grade = CtcaeGrade.GRADE_3;
            ae1.eventType = "headache";
            ae1.actuality = EventActuality.ACTUAL;
            ae1.outcome = AeOutcome.ONGOING;
            ae1.occurredAt = Instant.now();
            ae1.reportedAt = Instant.now();
            ae1.tenantId = "default";
            ae1.persist();

            AdverseEvent ae2 = new AdverseEvent();
            ae2.id = UUID.randomUUID();
            ae2.enrollmentId = enrollment.id;
            ae2.grade = CtcaeGrade.GRADE_1;
            ae2.eventType = "mild-nausea";
            ae2.actuality = EventActuality.ACTUAL;
            ae2.outcome = AeOutcome.RESOLVED;
            ae2.occurredAt = Instant.now();
            ae2.reportedAt = Instant.now();
            ae2.tenantId = "default";
            ae2.persist();
        }
        return trial.id;
    }

    @Transactional
    TrialSafetySignal findSignal(UUID trialId, String signalType) {
        return TrialSafetySignal.findByTrialAndType(trialId, signalType, "default");
    }

    List<WorkItem> dsmbBatchWorkItems() {
        TrialSafetySignal signal = TrialSafetySignal.findByTrialAndType(trialId, "GRADE_THRESHOLD", "default");
        if (signal == null) return List.of();
        String expectedCallerRef = "clinical:trial-safety-signal/" + signal.id;
        return workItemQueries.scanAll().stream()
            .filter(wi -> expectedCallerRef.equals(wi.callerRef()))
            .filter(wi -> !wi.status().isTerminal())
            .toList();
    }

    @Transactional
    void completeWorkItem(UUID workItemId) {
        workItemService.completeFromSystem(workItemId, "Reviewed — no action required", "dsmb-reviewer");
    }
}
