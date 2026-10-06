package io.casehub.clinical.scenario;

import io.casehub.clinical.api.model.ConsentStatus;
import io.casehub.clinical.api.model.CtcaeGrade;
import io.casehub.clinical.api.model.DeviationSeverity;
import io.casehub.clinical.api.model.EnrollmentStatus;
import io.casehub.clinical.api.model.EventActuality;
import io.casehub.clinical.api.model.TrialPhase;
import io.casehub.clinical.api.model.TrialStatus;
import io.casehub.clinical.entity.AdverseEvent;
import io.casehub.clinical.entity.ClinicalTrial;
import io.casehub.clinical.entity.PatientEnrollment;
import io.casehub.clinical.entity.ProtocolDeviation;
import io.casehub.clinical.entity.TrialSite;
import io.casehub.clinical.service.AdverseEventService;
import io.casehub.clinical.service.ProtocolDeviationService;
import io.casehub.clinical.service.TrialActivationService;
import io.casehub.ledger.runtime.service.LedgerVerificationService;
import io.casehub.pages.playbook.client.ActionContext;
import io.casehub.pages.playbook.client.PlaybookAction;
import io.casehub.platform.api.identity.CurrentPrincipal;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class ClinicalPlaybookActions {

    @Inject CurrentPrincipal principal;
    @Inject TrialActivationService trialActivationService;
    @Inject AdverseEventService adverseEventService;
    @Inject ProtocolDeviationService deviationService;
    @Inject LedgerVerificationService ledgerVerificationService;
    @Inject EntityManager em;

    @PlaybookAction("createTrial")
    @Transactional
    public Map<String, Object> createTrial(ActionContext ctx) {
        ClinicalTrial trial = new ClinicalTrial();
        trial.id = UUID.randomUUID();
        trial.protocolId = ctx.data("protocolId");
        trial.phase = TrialPhase.valueOf(ctx.data("phase"));
        trial.sponsor = ctx.data("sponsor");
        trial.targetEnrollment = ctx.data("targetEnrollment", Integer.class);
        trial.status = TrialStatus.PLANNING;
        trial.tenantId = principal.tenancyId();
        em.persist(trial);
        return Map.of("trialId", trial.id.toString());
    }

    @PlaybookAction("activateTrial")
    public Map<String, Object> activateTrial(ActionContext ctx) {
        UUID trialId = UUID.fromString(ctx.data("trialId"));
        trialActivationService.activate(trialId);
        return Map.of("status", "RECRUITING");
    }

    @PlaybookAction("addSite")
    @Transactional
    public Map<String, Object> addSite(ActionContext ctx) {
        UUID trialId = UUID.fromString(ctx.data("trialId"));
        ClinicalTrial trial = em.find(ClinicalTrial.class, trialId);
        TrialSite site = new TrialSite();
        site.id = UUID.randomUUID();
        site.trialId = trialId;
        site.investigatorId = ctx.data("investigatorId");
        site.tenantId = trial.tenantId;
        em.persist(site);
        return Map.of("siteId", site.id.toString());
    }

    @PlaybookAction("enrollPatient")
    @Transactional
    public Map<String, Object> enrollPatient(ActionContext ctx) {
        UUID siteId = UUID.fromString(ctx.data("siteId"));
        TrialSite site = em.find(TrialSite.class, siteId);
        PatientEnrollment enrollment = new PatientEnrollment();
        enrollment.id = UUID.randomUUID();
        enrollment.siteId = siteId;
        enrollment.patientId = ctx.data("patientId");
        enrollment.enrollmentStatus = EnrollmentStatus.CANDIDATE;
        enrollment.consentStatus = ConsentStatus.OBTAINED;
        enrollment.tenantId = site.tenantId;
        em.persist(enrollment);
        return Map.of("enrollmentId", enrollment.id.toString());
    }

    @PlaybookAction("reportAdverseEvent")
    public Map<String, Object> reportAdverseEvent(ActionContext ctx) {
        UUID enrollmentId = UUID.fromString(ctx.data("enrollmentId"));
        UUID siteId = UUID.fromString(ctx.data("siteId"));
        UUID trialId = UUID.fromString(ctx.data("trialId"));
        CtcaeGrade grade = CtcaeGrade.valueOf(ctx.data("grade"));

        AdverseEvent ae = new AdverseEvent();
        ae.id = UUID.randomUUID();
        ae.enrollmentId = enrollmentId;
        ae.grade = grade;
        ae.actuality = EventActuality.ACTUAL;
        ae.unexpected = "true".equals(ctx.data("unexpected"));
        ae.suspected = "true".equals(ctx.data("suspected"));
        ae.occurredAt = Instant.now();
        ae.reportedAt = Instant.now();
        ae.slaDeadline = Instant.now().plus(grade.sla().orElseThrow());
        ae.tenantId = principal.tenancyId();

        adverseEventService.reportAdverseEvent(ae);

        return Map.of("aeId", ae.id.toString(), "slaDeadline", ae.slaDeadline.toString());
    }

    @PlaybookAction("reportDeviation")
    public Map<String, Object> reportDeviation(ActionContext ctx) {
        UUID siteId = UUID.fromString(ctx.data("siteId"));
        TrialSite site = em.find(TrialSite.class, siteId);

        ProtocolDeviation deviation = new ProtocolDeviation();
        deviation.id = UUID.randomUUID();
        deviation.siteId = siteId;
        deviation.deviationType = ctx.data("deviationType");
        deviation.severity = DeviationSeverity.valueOf(ctx.data("severity"));
        deviation.tenantId = site.tenantId;

        deviationService.reportDeviation(deviation);

        return Map.of("deviationId", deviation.id.toString());
    }

    @PlaybookAction("verifyLedger")
    public Map<String, Object> verifyLedger(ActionContext ctx) {
        UUID enrollmentId = UUID.fromString(ctx.data("enrollmentId"));
        boolean valid = ledgerVerificationService.verify(enrollmentId, "default");
        String merkleRoot = ledgerVerificationService.treeRoot(enrollmentId, "default");
        return Map.of("valid", valid, "merkleRoot", merkleRoot != null ? merkleRoot : "");
    }
}
