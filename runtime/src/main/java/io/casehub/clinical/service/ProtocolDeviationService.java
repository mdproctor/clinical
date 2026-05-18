package io.casehub.clinical.service;

import io.casehub.clinical.api.model.PiApprovalStatus;
import io.casehub.clinical.api.spi.DeviationContext;
import io.casehub.clinical.api.spi.DeviationResponsePolicy;
import io.casehub.clinical.api.spi.DeviationResponseRequirements;
import io.casehub.clinical.entity.ClinicalTrial;
import io.casehub.clinical.entity.ProtocolDeviation;
import io.casehub.clinical.entity.TrialSite;
import io.casehub.ledger.api.model.ActorType;
import io.casehub.qhorus.api.channel.ChannelSemantic;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.runtime.channel.ChannelService;
import io.casehub.qhorus.runtime.message.MessageService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.Instant;

/**
 * Orchestrates the PI authorisation COMMAND lifecycle for protocol deviations.
 *
 * <p>Per GCP/ICH E6(R3): every protocol deviation requires a named Principal Investigator
 * to formally acknowledge and authorise (or reject) the deviation. This service:
 * <ol>
 *   <li>Creates a per-deviation oversight channel in Qhorus</li>
 *   <li>Sends a COMMAND message addressed to the PI — auto-opens a Commitment via MessageService</li>
 *   <li>Stamps the deviation with channel name, command timestamp, response deadline, and escalation tier</li>
 *   <li>Writes a tamper-evident {@link io.casehub.clinical.ledger.ProtocolDeviationLedgerEntry} via DeviationLedgerWriter</li>
 * </ol>
 */
@ApplicationScoped
public class ProtocolDeviationService {

    static final String CLINICAL_SENDER = "clinical-service";
    static final String CHANNEL_ALLOWED_TYPES = "QUERY,COMMAND";

    @Inject DeviationResponsePolicy policy;
    @Inject ChannelService channelService;
    @Inject MessageService messageService;
    @Inject DeviationLedgerWriter ledgerWriter;

    @Transactional
    public void reportDeviation(ProtocolDeviation deviation) {
        TrialSite site = TrialSite.findById(deviation.siteId);
        ClinicalTrial trial = ClinicalTrial.findById(site.trialId);

        var context = new DeviationContext(
            deviation.id, deviation.siteId, site.trialId,
            trial.protocolId, trial.phase, deviation.severity, deviation.deviationType
        );
        DeviationResponseRequirements requirements = policy.evaluate(context);

        String channelName = "clinical/deviation/" + deviation.id + "/pi-oversight";
        ensureChannel(channelName);

        var channel = channelService.findByName(channelName).orElseThrow();
        Instant now = Instant.now();
        Instant responseDeadline = now.plus(requirements.piResponseDeadline());
        String content = buildCommandContent(deviation, responseDeadline);

        messageService.send(
            channel.id,
            CLINICAL_SENDER,
            MessageType.COMMAND,
            content,
            deviation.id.toString(),
            null,
            null,
            site.investigatorId,
            ActorType.SYSTEM
        );

        deviation.piCommandChannelName = channelName;
        deviation.commandedAt = now;
        deviation.responseDeadline = responseDeadline;
        deviation.escalationRequirement = requirements.escalationRequirement();
        deviation.piApprovalStatus = PiApprovalStatus.COMMANDED;
        deviation.persist();

        ledgerWriter.writeCommandEntry(deviation, site.investigatorId);
    }

    private void ensureChannel(String name) {
        if (channelService.findByName(name).isPresent()) {
            return;
        }
        channelService.create(
            name,
            "PI governance channel for protocol deviation",
            ChannelSemantic.APPEND,
            null, null, null, null, null,
            CHANNEL_ALLOWED_TYPES
        );
    }

    private String buildCommandContent(ProtocolDeviation dev, Instant responseDeadline) {
        return "{\"deviationId\":\"" + dev.id
            + "\",\"deviationType\":\"" + dev.deviationType
            + "\",\"severity\":\"" + dev.severity
            + "\",\"responseDeadline\":\"" + responseDeadline
            + "\"}";
    }
}
