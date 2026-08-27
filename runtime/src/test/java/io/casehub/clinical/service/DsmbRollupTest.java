package io.casehub.clinical.service;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.casehub.api.engine.CaseHubRuntime;
import io.casehub.clinical.api.AdverseEventReportedEvent;
import io.casehub.clinical.api.model.CtcaeGrade;
import io.casehub.clinical.entity.AdverseEvent;
import io.casehub.clinical.entity.ClinicalTrial;
import io.casehub.clinical.entity.TrialSite;
import io.casehub.clinical.api.model.AeOutcome;
import io.casehub.clinical.api.model.EventActuality;
import io.casehub.clinical.support.WorkItemCompletionCapture;
import io.casehub.clinical.support.WorkItemQueries;
import io.casehub.work.api.WorkItem;
import io.casehub.work.runtime.service.WorkItemService;
import io.casehub.work.engine.WorkItemLifecycleAdapter;
import io.casehub.platform.api.identity.CurrentPrincipal;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Layer 6 showcase: trial-level DSMB rollup fires when two sites simultaneously
 * have active Grade 4+ adverse events.
 */
@QuarkusTest
class DsmbRollupTest {

    @Inject AeEscalationCaseService aeEscalationCaseService;
    @Inject TrialActivationService trialActivationService;
    @Inject WorkItemQueries workItemQueries;
    @Inject WorkItemService workItemService;
    @Inject WorkItemCompletionCapture completionCapture;
    @Inject WorkItemLifecycleAdapter lifecycleAdapter;
    @Inject CaseHubRuntime runtime;
    @Inject CurrentPrincipal principal;

    private UUID trialId;
    private UUID siteAId;
    private UUID siteBId;

    @BeforeEach
    void setup() {
        trialId = createAndActivateTrial();
        siteAId = createSite(trialId);
        siteBId = createSite(trialId);
        completionCapture.reset();
    }

    @Test
    void two_sites_grade4_triggers_dsmb_rollup_workitem() {
        // Site A: Grade 4 AE reported
        aeEscalationCaseService.onAdverseEventReported(aeEvent(siteAId, CtcaeGrade.GRADE_4));

        // Await: no DSMB rollup yet — only one site
        await().atMost(3, SECONDS).pollInterval(100, MILLISECONDS)
                .untilAsserted(() ->
                        assertThat(dsmbRollupWorkItems()).as("No DSMB rollup with one site").isEmpty());

        // Site B: Grade 4 AE reported — now two sites active
        aeEscalationCaseService.onAdverseEventReported(aeEvent(siteBId, CtcaeGrade.GRADE_4));

        // Await: DSMB rollup WorkItem created by trial case binding
        await().atMost(5, SECONDS).pollInterval(100, MILLISECONDS)
                .untilAsserted(() -> {
                    List<WorkItem> items = dsmbRollupWorkItems();
                    assertThat(items).as("DSMB rollup WorkItem").isNotEmpty();
                    assertThat(items.get(0).title()).contains("DSMB review");
                    assertThat(items.get(0).title()).contains("multiple sites");
                });
    }

    @Test
    void single_site_grade4_does_not_trigger_dsmb_rollup() {
        aeEscalationCaseService.onAdverseEventReported(aeEvent(siteAId, CtcaeGrade.GRADE_4));

        await().during(2, SECONDS).atMost(3, SECONDS)
                .untilAsserted(() ->
                        assertThat(dsmbRollupWorkItems()).as("No DSMB rollup with only one site").isEmpty());
    }

    @Test
    void two_sites_grade3_does_not_trigger_dsmb_rollup() {
        aeEscalationCaseService.onAdverseEventReported(aeEvent(siteAId, CtcaeGrade.GRADE_3));
        aeEscalationCaseService.onAdverseEventReported(aeEvent(siteBId, CtcaeGrade.GRADE_3));

        await().during(2, SECONDS).atMost(3, SECONDS)
                .untilAsserted(() ->
                        assertThat(dsmbRollupWorkItems()).as("Grade 3 AEs do not trigger DSMB rollup").isEmpty());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private List<WorkItem> dsmbRollupWorkItems() {
        return workItemQueries.scanAll().stream()
                .filter(wi -> wi.title() != null && wi.title().contains("DSMB review")
                        && wi.title().contains("multiple sites"))
                .toList();
    }

    /**
     * Persists an AdverseEvent row and returns the matching AdverseEventReportedEvent.
     * Required after the three-phase refactor: Phase 1 calls AdverseEvent.findById() —
     * without a persisted row the service logs a warning and skips escalation.
     */
    @Transactional
    AdverseEventReportedEvent aeEvent(UUID siteId, CtcaeGrade grade) {
        UUID aeId = UUID.randomUUID();
        UUID enrollmentId = UUID.randomUUID();
        AdverseEvent ae = new AdverseEvent();
        ae.id = aeId;
        ae.enrollmentId = enrollmentId;
        ae.grade = grade;
        ae.actuality = EventActuality.ACTUAL;
        ae.outcome = AeOutcome.ONGOING;
        ae.occurredAt = Instant.now();
        ae.reportedAt = Instant.now();
        ae.persist();
        return new AdverseEventReportedEvent(aeId, enrollmentId, siteId, grade, Instant.now(), "test-tenant");
    }

    UUID createAndActivateTrial() {
        UUID trialId = persistTrial();
        trialActivationService.activate(trialId);
        return trialId;
    }

    @Transactional
    UUID persistTrial() {
        ClinicalTrial trial = new ClinicalTrial();
        trial.id = UUID.randomUUID();
        trial.protocolId = "DSMB-TEST-001";
        trial.phase = io.casehub.clinical.api.model.TrialPhase.PHASE_III;
        trial.sponsor = "TestSponsor";
        trial.targetEnrollment = 100;
        trial.status = io.casehub.clinical.api.model.TrialStatus.PLANNING;
        trial.tenantId = principal.tenancyId();
        trial.persist();
        return trial.id;
    }

    @Transactional
    UUID createSite(UUID trialId) {
        TrialSite site = new TrialSite();
        site.id = UUID.randomUUID();
        site.trialId = trialId;
        site.investigatorId = "pi-" + UUID.randomUUID();
        site.status = io.casehub.clinical.api.model.SiteStatus.ACTIVE;
        site.persist();
        return site.id;
    }
}
