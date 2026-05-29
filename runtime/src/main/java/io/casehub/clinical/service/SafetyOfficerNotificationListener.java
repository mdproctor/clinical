package io.casehub.clinical.service;

import io.casehub.clinical.api.AdverseEventReportedEvent;
import io.casehub.clinical.api.SafetyOfficerNotificationRequest;
import io.casehub.clinical.api.SafetyOfficerNotifier;
import io.casehub.clinical.entity.ClinicalTrial;
import io.casehub.clinical.entity.TrialSite;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class SafetyOfficerNotificationListener {

    @Inject SafetyOfficerNotifier notifier;

    @Transactional
    public void onAeReported(@ObservesAsync final AdverseEventReportedEvent event) {
        if (event.siteId() == null) {
            Log.errorf("AE %s has no siteId — safety officer notification skipped", event.aeId());
            return;
        }
        final TrialSite site = TrialSite.findById(event.siteId());
        if (site == null) {
            Log.warnf("TrialSite %s not found — safety officer notification skipped", event.siteId());
            return;
        }
        final ClinicalTrial trial = ClinicalTrial.findById(site.trialId);
        if (trial == null) {
            Log.warnf("Trial %s not found — safety officer notification skipped", site.trialId);
            return;
        }
        if (trial.safetyOfficerConnectorId == null || trial.safetyOfficerDestination == null) {
            Log.warnf("Trial %s has incomplete safety officer notification config (connectorId=%s, destination=%s) — skipping",
                site.trialId, trial.safetyOfficerConnectorId, trial.safetyOfficerDestination);
            return;
        }
        notifier.notify(new SafetyOfficerNotificationRequest(
            event.aeId(), event.enrollmentId(), event.siteId(), event.grade(),
            trial.safetyOfficerConnectorId, trial.safetyOfficerDestination));
    }
}
