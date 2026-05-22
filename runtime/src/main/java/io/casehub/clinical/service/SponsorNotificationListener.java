package io.casehub.clinical.service;

import io.casehub.clinical.api.ProtocolDeviationResolvedEvent;
import io.casehub.clinical.api.SponsorNotificationRequest;
import io.casehub.clinical.api.SponsorNotifier;
import io.casehub.clinical.api.model.EscalationRequirement;
import io.casehub.clinical.entity.ClinicalTrial;
import io.casehub.clinical.entity.TrialSite;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class SponsorNotificationListener {

    @Inject SponsorNotifier sponsorNotifier;

    @Transactional
    public void onDeviationResolved(@ObservesAsync ProtocolDeviationResolvedEvent event) {
        if (event.escalationRequirement() != EscalationRequirement.SPONSOR_NOTIFICATION) return;

        TrialSite site = TrialSite.findById(event.siteId());
        if (site == null) {
            Log.warnf("TrialSite %s not found — sponsor notification skipped", event.siteId());
            return;
        }

        ClinicalTrial trial = ClinicalTrial.findById(site.trialId);
        if (trial == null) {
            Log.warnf("Trial %s not found — sponsor notification skipped", site.trialId);
            return;
        }
        if (trial.sponsorNotificationConnectorId == null || trial.sponsorNotificationDestination == null) {
            Log.warnf("Trial %s has incomplete sponsor notification config (connectorId=%s, destination=%s) — skipping",
                site.trialId, trial.sponsorNotificationConnectorId, trial.sponsorNotificationDestination);
            return;
        }

        sponsorNotifier.notify(new SponsorNotificationRequest(
            site.trialId,
            event.siteId(),
            event.deviationId(),
            event.deviationType(),
            event.severity(),
            event.terminalStatus(),
            event.piId(),
            trial.sponsorNotificationConnectorId,
            trial.sponsorNotificationDestination
        ));
    }
}
