package io.casehub.clinical.service;

import io.casehub.clinical.api.ProtocolDeviationResolvedEvent;
import io.casehub.clinical.api.model.EscalationRequirement;
import io.casehub.clinical.api.model.PiApprovalStatus;
import io.casehub.clinical.entity.ProtocolDeviation;
import io.casehub.ledger.api.model.ActorType;
import io.casehub.qhorus.api.gateway.MessageReceivedEvent;
import io.casehub.qhorus.api.message.MessageType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ApplicationScoped
public class PiResponseListener {

    private static final Pattern CHANNEL_PATTERN =
        Pattern.compile("clinical/deviation/([0-9a-f-]+)/pi-oversight");

    @Inject Event<ProtocolDeviationResolvedEvent> resolvedEvent;
    @Inject DeviationLedgerWriter ledgerWriter;

    @Transactional
    public void onMessage(@ObservesAsync MessageReceivedEvent event) {
        if (event.messageType() != MessageType.DONE && event.messageType() != MessageType.DECLINE) return;
        process(event.channelName(), event.messageType(), event.senderId());
    }

    @Transactional
    public void process(String channelName, MessageType messageType, String senderId) {
        if (messageType != MessageType.DONE && messageType != MessageType.DECLINE) return;

        Matcher m = CHANNEL_PATTERN.matcher(channelName);
        if (!m.matches()) return;

        UUID deviationId = UUID.fromString(m.group(1));
        ProtocolDeviation deviation = ProtocolDeviation.findById(deviationId);
        if (deviation == null) return;
        if (deviation.piApprovalStatus != PiApprovalStatus.COMMANDED) return;

        boolean approved = messageType == MessageType.DONE;

        if (approved) {
            boolean needsEscalation = deviation.escalationRequirement != null
                && deviation.escalationRequirement != EscalationRequirement.NONE;
            deviation.piApprovalStatus = needsEscalation
                ? PiApprovalStatus.ESCALATED : PiApprovalStatus.APPROVED;
        } else {
            deviation.piApprovalStatus = PiApprovalStatus.REJECTED;
        }

        ledgerWriter.writeResolutionEntry(deviation, deviation.piApprovalStatus,
            senderId, ActorType.HUMAN, "pi-authoriser");

        resolvedEvent.fireAsync(new ProtocolDeviationResolvedEvent(
            deviation.id, deviation.siteId, deviation.severity,
            deviation.escalationRequirement != null
                ? deviation.escalationRequirement : EscalationRequirement.NONE,
            deviation.piApprovalStatus,
            deviation.deviationType,
            senderId
        ));
    }
}
