package io.casehub.clinical.service;

import io.casehub.clinical.api.model.AeOutcome;
import io.casehub.clinical.api.model.ConsentStatus;
import io.casehub.clinical.api.model.CtcaeGrade;
import io.casehub.clinical.api.model.EnrollmentStatus;
import io.casehub.clinical.api.model.EventActuality;
import io.casehub.clinical.api.model.TrialPhase;
import io.casehub.clinical.entity.AdverseEvent;
import io.casehub.clinical.entity.ClinicalTrial;
import io.casehub.clinical.entity.PatientEnrollment;
import io.casehub.clinical.entity.TrialSite;
import io.casehub.clinical.ledger.AdverseEventLedgerEntry;
import io.casehub.ledger.runtime.repository.LedgerEntryRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@QuarkusTest
class AdverseEventServiceTest {

    @Inject
    AdverseEventService service;

    @Inject
    LedgerEntryRepository ledgerRepo;

    @Test
    @Transactional
    void grade3_slaDeadline_is_reportedAt_plus_24h() {
        AdverseEvent ae = newAe(CtcaeGrade.GRADE_3);
        service.reportAdverseEvent(ae);
        assertThat(ae.slaDeadline)
            .isCloseTo(ae.reportedAt.plus(Duration.ofHours(24)), within(5, ChronoUnit.SECONDS));
    }

    @Test
    @Transactional
    void grade5_slaDeadline_is_reportedAt_plus_1h() {
        AdverseEvent ae = newAe(CtcaeGrade.GRADE_5);
        service.reportAdverseEvent(ae);
        assertThat(ae.slaDeadline)
            .isCloseTo(ae.reportedAt.plus(Duration.ofHours(1)), within(5, ChronoUnit.SECONDS));
    }

    @Test
    @Transactional
    void grade1_slaDeadline_is_reportedAt_plus_7days() {
        AdverseEvent ae = newAe(CtcaeGrade.GRADE_1);
        service.reportAdverseEvent(ae);
        assertThat(ae.slaDeadline)
            .isCloseTo(ae.reportedAt.plus(Duration.ofDays(7)), within(5, ChronoUnit.SECONDS));
    }

    @Test
    @Transactional
    void grade1_workItemId_is_set_directly() {
        AdverseEvent ae = newAe(CtcaeGrade.GRADE_1);
        service.reportAdverseEvent(ae);
        assertThat(ae.workItemId).as("Grade 1 uses direct WorkItem creation").isNotNull();
    }

    @Test
    @Transactional
    void grade3_workItemId_is_null_engine_manages_it() {
        AdverseEvent ae = newAe(CtcaeGrade.GRADE_3);
        service.reportAdverseEvent(ae);
        assertThat(ae.workItemId)
            .as("Grade 3 is engine-managed; workItemId set by engine, not service")
            .isNull();
    }

    @Test
    @Transactional
    void grade4_workItemId_is_null_engine_manages_it() {
        AdverseEvent ae = newAe(CtcaeGrade.GRADE_4);
        service.reportAdverseEvent(ae);
        assertThat(ae.workItemId).isNull();
    }

    @Test
    @Transactional
    void reportedAt_is_set_server_side() {
        AdverseEvent ae = newAe(CtcaeGrade.GRADE_3);
        Instant before = Instant.now().minusSeconds(1);
        service.reportAdverseEvent(ae);
        assertThat(ae.reportedAt).isAfterOrEqualTo(before);
    }

    @Test
    @Transactional
    void ledger_entry_is_persisted_with_correct_fields() {
        AdverseEvent ae = newAe(CtcaeGrade.GRADE_4);
        service.reportAdverseEvent(ae);

        var entries = ledgerRepo.findBySubjectId(ae.id);
        assertThat(entries).hasSize(1);
        AdverseEventLedgerEntry entry = (AdverseEventLedgerEntry) entries.get(0);
        assertThat(entry.adverseEventId).isEqualTo(ae.id);
        assertThat(entry.enrollmentId).isEqualTo(ae.enrollmentId);
        assertThat(entry.ctcaeGrade).isEqualTo("GRADE_4");
        assertThat(entry.reportedAt).isEqualTo(ae.reportedAt);
        assertThat(entry.slaDeadline).isEqualTo(ae.slaDeadline);
    }

    private AdverseEvent newAe(CtcaeGrade grade) {
        ClinicalTrial trial = new ClinicalTrial();
        trial.id = UUID.randomUUID();
        trial.protocolId = "SVC-TEST-" + trial.id;
        trial.phase = TrialPhase.PHASE_II;
        trial.sponsor = "Test";
        trial.targetEnrollment = 10;
        trial.persist();

        TrialSite site = new TrialSite();
        site.id = UUID.randomUUID();
        site.trialId = trial.id;
        site.investigatorId = "pi-svc-test";
        site.persist();

        PatientEnrollment enrollment = new PatientEnrollment();
        enrollment.id = UUID.randomUUID();
        enrollment.siteId = site.id;
        enrollment.patientId = "PAT-SVC-" + enrollment.id;
        enrollment.consentStatus = ConsentStatus.PENDING;
        enrollment.enrollmentStatus = EnrollmentStatus.CANDIDATE;
        enrollment.persist();

        AdverseEvent ae = new AdverseEvent();
        ae.id = UUID.randomUUID();
        ae.enrollmentId = enrollment.id;
        ae.grade = grade;
        ae.actuality = EventActuality.ACTUAL;
        ae.outcome = AeOutcome.ONGOING;
        ae.occurredAt = Instant.now().minus(Duration.ofHours(2));
        return ae;
    }
}
