package io.casehub.clinical.service;

import io.casehub.clinical.api.SponsorNotificationRequest;
import io.casehub.clinical.api.SponsorNotifier;
import io.casehub.clinical.api.model.PiApprovalStatus;
import io.casehub.clinical.entity.ProtocolDeviation;
import io.casehub.connectors.Connector;
import io.casehub.connectors.ConnectorMessage;
import io.quarkus.arc.DefaultBean;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.Clock;
import java.time.Instant;

@ApplicationScoped
@DefaultBean
public class DefaultSponsorNotifier implements SponsorNotifier {

    @Inject @Any Instance<Connector> connectors;
    @Inject DeviationLedgerWriter ledgerWriter;
    @Inject Clock clock;

    @Override
    public void notify(SponsorNotificationRequest req) {
        Connector connector = connectors.stream()
            .filter(c -> c.id().equals(req.sponsorNotificationConnectorId()))
            .findFirst()
            .orElse(null);

        if (connector == null) {
            Log.errorf("No connector '%s' found — sponsor notification not delivered for deviation %s",
                req.sponsorNotificationConnectorId(), req.deviationId());
            recordAttempt(req, false);
            return;
        }

        try {
            connector.send(new ConnectorMessage(
                req.sponsorNotificationDestination(),
                buildTitle(req),
                buildBody(req)
            ));
            recordAttempt(req, true);
        } catch (Exception e) {
            Log.errorf(e, "Sponsor notification delivery failed for deviation %s", req.deviationId());
            recordAttempt(req, false);
        }
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    protected void recordAttempt(SponsorNotificationRequest req, boolean delivered) {
        ProtocolDeviation dev = ProtocolDeviation.findById(req.deviationId());
        if (dev == null) {
            Log.errorf("ProtocolDeviation %s not found — cannot write ledger entry", req.deviationId());
            return;
        }
        ledgerWriter.writeSponsorNotifiedEntry(dev, clock.instant(), delivered);
    }

    private String buildTitle(SponsorNotificationRequest req) {
        return "[" + req.severity().name() + " Deviation] " + req.deviationType() + " — " + req.terminalStatus().name();
    }

    private String buildBody(SponsorNotificationRequest req) {
        return switch (req.terminalStatus()) {
            case ESCALATED -> "PI " + req.piId() + " approved — corrective action committed. " +
                "Site: " + req.siteId() + ". Type: " + req.deviationType() + ". " +
                "Ref: clinical/deviation/" + req.deviationId() + "/pi-oversight";
            case REJECTED -> "PI " + req.piId() + " refused to authorise — no corrective action. " +
                "Site: " + req.siteId() + ". Type: " + req.deviationType() + ".";
            case EXPIRED -> "PI response deadline expired — no response received. " +
                "Site: " + req.siteId() + ". Type: " + req.deviationType() + ".";
            default -> throw new IllegalArgumentException(
                "Unexpected terminal status for sponsor notification: " + req.terminalStatus());
        };
    }
}
