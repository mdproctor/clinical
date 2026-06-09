package io.casehub.clinical.service;

import io.casehub.clinical.api.ProtocolDeviationResolvedEvent;
import io.casehub.clinical.api.SponsorNotificationRequest;
import io.casehub.clinical.api.SponsorNotifier;
import io.casehub.clinical.api.model.EscalationRequirement;
import io.casehub.clinical.api.spi.PiIdentityResolver;
import io.casehub.clinical.entity.ClinicalTrial;
import io.casehub.clinical.entity.TrialSite;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Clock;

@ApplicationScoped
public class SponsorNotificationListener {

    @Inject SponsorNotifier sponsorNotifier;
    @Inject DeviationLedgerWriter deviationLedgerWriter;
    @Inject PiIdentityResolver piIdentityResolver;
    @Inject Clock clock;

    @Transactional
    public void onDeviationResolved(@ObservesAsync ProtocolDeviationResolvedEvent event) {
        if (event.escalationRequirement() != EscalationRequirement.SPONSOR_NOTIFICATION) return;

        try {
            TrialSite site = TrialSite.findById(event.siteId());
            if (site == null) {
                Log.warnf("TrialSite %s not found — sponsor notification skipped", event.siteId());
                try {
                    deviationLedgerWriter.writeSkippedSponsorEntry(event.deviationId(), event.siteId(), event.severity(), clock.instant(), "sponsor-notifier-skipped-site-not-found");
                } catch (Exception writeEx) {
                    Log.errorf(writeEx, "AUDIT GAP: could not write skipped entry for deviation %s", event.deviationId());
                }
                return;
            }

            ClinicalTrial trial = ClinicalTrial.findById(site.trialId);
            if (trial == null) {
                Log.warnf("Trial %s not found — sponsor notification skipped", site.trialId);
                try {
                    deviationLedgerWriter.writeSkippedSponsorEntry(event.deviationId(), event.siteId(), event.severity(), clock.instant(), "sponsor-notifier-skipped-trial-not-found");
                } catch (Exception writeEx) {
                    Log.errorf(writeEx, "AUDIT GAP: could not write skipped entry for deviation %s", event.deviationId());
                }
                return;
            }
            if (trial.sponsorNotificationConnectorId == null || trial.sponsorNotificationDestination == null) {
                Log.warnf("Trial %s has incomplete sponsor notification config (connectorId=%s, destination=%s) — skipping",
                    site.trialId, trial.sponsorNotificationConnectorId, trial.sponsorNotificationDestination);
                try {
                    deviationLedgerWriter.writeSkippedSponsorEntry(event.deviationId(), event.siteId(), event.severity(), clock.instant(), "sponsor-notifier-skipped-no-config");
                } catch (Exception writeEx) {
                    Log.errorf(writeEx, "AUDIT GAP: could not write skipped entry for deviation %s", event.deviationId());
                }
                return;
            }

            // Resolve PI formal name before delivery — null for EXPIRED (system-initiated, no PI actor).
            // Resolution failure is a distinct audit role from delivery failure.
            String piDisplayName = null;
            if (event.piId() != null) {
                try {
                    piDisplayName = piIdentityResolver.resolveFormalName(event.piId());
                    if (piDisplayName == null) {
                        // Contract violation — implementations must not return null. Treat as a fault.
                        Log.errorf("PiIdentityResolver returned null for piId=%s (deviation %s) — writing resolver-failed entry",
                            event.piId(), event.deviationId());
                        try {
                            deviationLedgerWriter.writeSkippedSponsorEntry(event.deviationId(), event.siteId(),
                                event.severity(), clock.instant(), "sponsor-notifier-pi-resolver-failed");
                        } catch (Exception writeEx) {
                            Log.errorf(writeEx, "AUDIT GAP: could not write resolver-failed entry for deviation %s",
                                event.deviationId());
                        }
                        return;
                    }
                } catch (Exception resolveEx) {
                    Log.errorf(resolveEx, "PI identity resolution failed for deviation %s — writing resolver-failed entry",
                        event.deviationId());
                    try {
                        deviationLedgerWriter.writeSkippedSponsorEntry(event.deviationId(), event.siteId(),
                            event.severity(), clock.instant(), "sponsor-notifier-pi-resolver-failed");
                    } catch (Exception writeEx) {
                        Log.errorf(writeEx, "AUDIT GAP: could not write resolver-failed entry for deviation %s",
                            event.deviationId());
                    }
                    return;
                }
            }

            sponsorNotifier.notify(new SponsorNotificationRequest(
                site.trialId,
                event.siteId(),
                event.deviationId(),
                event.deviationType(),
                event.severity(),
                event.terminalStatus(),
                event.piId(),
                piDisplayName,
                trial.sponsorNotificationConnectorId,
                trial.sponsorNotificationDestination,
                event.tenantId()
            ));
        } catch (Exception e) {
            Log.errorf(e, "Unexpected error in sponsor notification for deviation %s — writing failed ledger entry", event.deviationId());
            try {
                deviationLedgerWriter.writeObserverFailureEntry(
                    event.deviationId(), event.siteId(), event.severity(), clock.instant());
            } catch (Exception writeEx) {
                Log.errorf(writeEx, "AUDIT GAP: could not write observer failure entry for deviation %s", event.deviationId());
            }
        }
    }
}
