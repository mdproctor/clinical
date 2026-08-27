package io.casehub.clinical.service;

import io.casehub.clinical.api.model.CtcaeGrade;
import io.casehub.ledger.api.model.supplement.ComplianceSupplement;
import io.casehub.ledger.runtime.model.supplement.JpaComplianceSupplement;
import java.util.Objects;

/**
 * Factory for {@link ComplianceSupplement} instances covering each type of AI agent
 * decision in the clinical trial harness.
 *
 * Each supplement records the regulatory reference (planRef) and the algorithm or
 * policy component that made the decision (algorithmRef). All clinical AI decisions
 * are subject to human override — {@code humanOverrideAvailable = true} on every entry.
 *
 * Used by LedgerWriter beans to satisfy EU AI Act Art.12 and 21 CFR Part 312 audit
 * requirements for traceable, contestable AI decisions.
 */
public final class ClinicalComplianceSupplement {

    private ClinicalComplianceSupplement() {}

    public static ComplianceSupplement aeEscalation() {
        ComplianceSupplement s = new JpaComplianceSupplement();
        s.planRef = "ICH E6(R3) §5.17 — serious adverse event reporting";
        s.algorithmRef = "AdverseEventEscalationPolicy (rule-based CTCAE routing)";
        s.humanOverrideAvailable = true;
        return s;
    }

    public static ComplianceSupplement irbDecision() {
        ComplianceSupplement s = new JpaComplianceSupplement();
        s.planRef = "21 CFR Part 312 — IRB review and approval";
        s.algorithmRef = "IrbCommitteePolicySpi (configurable IRB routing)";
        s.humanOverrideAvailable = true;
        return s;
    }

    public static ComplianceSupplement protocolDeviation() {
        ComplianceSupplement s = new JpaComplianceSupplement();
        s.planRef = "ICH E6(R3) §4.5 — protocol deviation recording";
        s.algorithmRef = "ProtocolDeviationService (rule-based severity classification)";
        s.humanOverrideAvailable = true;
        return s;
    }

    public static ComplianceSupplement susarGateDecision() {
        ComplianceSupplement s = new JpaComplianceSupplement();
        s.planRef = "ICH E2A §I.A.1 + 21 CFR 312.32 — SUSAR criteria and expedited reporting";
        s.algorithmRef = "SusarCriteriaEvaluator (rule-based CTCAE Grade 4/5 unexpected/suspected)";
        s.humanOverrideAvailable = true;
        return s;
    }

    public static ComplianceSupplement safetyOfficerNotification() {
        ComplianceSupplement s = new JpaComplianceSupplement();
        s.planRef = "ICH E6(R3) §5.17 — safety officer notification on Grade 3+ AE";
        s.algorithmRef = "AdverseEventEscalationPolicy (rule-based CTCAE routing)";
        s.humanOverrideAvailable = true;
        return s;
    }

    public static ComplianceSupplement sponsorNotification() {
        ComplianceSupplement s = new JpaComplianceSupplement();
        s.planRef = "ICH E6(R3) §5.17 — sponsor notification on serious adverse event";
        s.algorithmRef = "AdverseEventEscalationPolicy (rule-based CTCAE routing)";
        s.humanOverrideAvailable = true;
        return s;
    }

    public static ComplianceSupplement eligibilityScreening() {
        ComplianceSupplement s = new JpaComplianceSupplement();
        s.planRef = "ICH E6(R3) §4.2 — patient eligibility assessment and IRB consultation";
        s.algorithmRef = "EligibilityScreeningService (rule-based FHIR criterion evaluation)";
        s.humanOverrideAvailable = true;
        return s;
    }

    public static ComplianceSupplement protocolAmendment() {
        ComplianceSupplement s = new JpaComplianceSupplement();
        s.planRef = "21 CFR Part 312 §312.30 — protocol amendment review";
        s.algorithmRef = "ProtocolAmendmentAdvisor (DefaultProtocolAmendmentAdvisor stub; engine#101 pending)";
        s.humanOverrideAvailable = true;
        return s;
    }

    public static ComplianceSupplement regulatorySubmission(CtcaeGrade grade) {
        Objects.requireNonNull(grade, "grade");
        ComplianceSupplement s = new JpaComplianceSupplement();
        s.planRef = switch (grade) {
            case GRADE_5 -> "21 CFR 312.32(c)(1)(i) — IND 7-day expedited safety reporting, unexpected fatal AE";
            case GRADE_4 -> "21 CFR 312.32(c)(1)(i) — IND 7-day expedited safety reporting, unexpected life-threatening AE";
            case GRADE_3 -> "21 CFR 312.32(c)(1)(ii) — IND 15-day expedited safety reporting, unexpected serious AE";
            default -> throw new IllegalArgumentException("no IND planRef for grade: " + grade);
        };
        s.algorithmRef = "RegulatorySubmissionCaseService (rule-based CTCAE grade + unexpected criteria)";
        s.humanOverrideAvailable = true;
        return s;
    }

    public static ComplianceSupplement regulatorySubmissionFiled(CtcaeGrade grade) {
        Objects.requireNonNull(grade, "grade");
        ComplianceSupplement s = new JpaComplianceSupplement();
        s.planRef = switch (grade) {
            case GRADE_5 -> "21 CFR 312.32(c)(1)(i) — IND 7-day expedited safety report filed, unexpected fatal AE";
            case GRADE_4 -> "21 CFR 312.32(c)(1)(i) — IND 7-day expedited safety report filed, unexpected life-threatening AE";
            case GRADE_3 -> "21 CFR 312.32(c)(1)(ii) — IND 15-day expedited safety report filed, unexpected serious AE";
            default -> throw new IllegalArgumentException("no IND planRef for grade: " + grade);
        };
        s.algorithmRef = "RegulatorySubmissionCompletedListener — WorkItem completed by regulatory-affairs";
        s.humanOverrideAvailable = true;
        return s;
    }

    public static ComplianceSupplement regulatorySubmissionBreach(CtcaeGrade grade) {
        Objects.requireNonNull(grade, "grade");
        ComplianceSupplement s = new JpaComplianceSupplement();
        s.planRef = switch (grade) {
            case GRADE_5 -> "21 CFR 312.32(c)(1)(i) — IND 7-day expedited reporting deadline missed, unexpected fatal AE";
            case GRADE_4 -> "21 CFR 312.32(c)(1)(i) — IND 7-day expedited reporting deadline missed, unexpected life-threatening AE";
            case GRADE_3 -> "21 CFR 312.32(c)(1)(ii) — IND 15-day expedited reporting deadline missed, unexpected serious AE";
            default -> throw new IllegalArgumentException("no IND planRef for grade: " + grade);
        };
        s.algorithmRef = "ClinicalIndReportingBreachPolicy — IND deadline exhausted";
        s.humanOverrideAvailable = true;
        return s;
    }

    public static ComplianceSupplement cbrRetrieval() {
        ComplianceSupplement s = new JpaComplianceSupplement();
        s.planRef                = "EU AI Act Art.12 — record-keeping for high-risk AI decision support";
        s.algorithmRef           = "ClinicalCbrService (CBR precedent retrieval, weighted feature similarity)";
        s.humanOverrideAvailable = true;
        return s;
    }

    public static ComplianceSupplement gradeChange() {
        ComplianceSupplement s = new JpaComplianceSupplement();
        s.planRef                = "ICH E6(R3) §5.17 — adverse event grade reassessment audit trail";
        s.algorithmRef           = "AdverseEventService.regradeAdverseEvent (clinician-initiated grade change)";
        s.humanOverrideAvailable = true;
        return s;
    }

    public static ComplianceSupplement safetySignalDetection() {
        ComplianceSupplement s = new JpaComplianceSupplement();
        s.planRef                = "ICH E6(R3) §5.17 + ICH E2F §3.3 — DSMB-level cross-site safety signal detection";
        s.algorithmRef           = "TrialSafetyAggregationJob (rule-based grade threshold and cross-site cluster detection)";
        s.humanOverrideAvailable = true;
        return s;
    }

    public static ComplianceSupplement dataCapture() {
        ComplianceSupplement s = new JpaComplianceSupplement();
        s.planRef                = "ICH E6(R3) §5.18 — clinical data capture audit trail";
        s.algorithmRef           = "ClinicalDataCaptureService (direct data entry by clinical staff)";
        s.humanOverrideAvailable = true;
        return s;
    }


}
