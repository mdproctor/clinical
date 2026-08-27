package io.casehub.clinical.service;

import io.casehub.qhorus.api.gateway.ChannelRef;
import io.casehub.qhorus.api.gateway.InboundHumanMessage;
import io.casehub.qhorus.api.gateway.InboundNormaliser;
import io.casehub.qhorus.api.gateway.NormalisedMessage;
import io.casehub.qhorus.api.message.MessageType;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ClinicalInboundNormaliser implements InboundNormaliser {

    @Override
    public NormalisedMessage normalise(ChannelRef channel, InboundHumanMessage raw) {
        // Only apply clinical decision parsing to PI oversight channels.
        // Other channels default to QUERY to avoid misclassifying unrelated messages.
        MessageType type = isOversightChannel(channel.name())
            ? detectDecision(raw.content())
            : MessageType.QUERY;
        return new NormalisedMessage(type, raw.content(), null, "human:" + raw.externalSenderId(),
            raw.correlationId(), null, null, null);
    }

    private boolean isOversightChannel(String channelName) {
        return channelName != null && channelName.contains("/pi-oversight");
    }

    private MessageType detectDecision(String content) {
        if (content == null) return MessageType.QUERY;
        // Tolerate whitespace around the colon — {"decision" : "APPROVED"} is valid JSON.
        String normalised = content.replaceAll("\\s", "");
        if (normalised.contains("\"decision\":\"APPROVED\"")) return MessageType.DONE;
        if (normalised.contains("\"decision\":\"REJECTED\"")) return MessageType.DECLINE;
        return MessageType.QUERY;
    }
}
