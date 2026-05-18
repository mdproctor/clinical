package io.casehub.clinical.service;

import io.casehub.qhorus.api.gateway.ChannelRef;
import io.casehub.qhorus.api.gateway.InboundHumanMessage;
import io.casehub.qhorus.api.message.MessageType;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class ClinicalInboundNormaliserTest {

    @Inject
    ClinicalInboundNormaliser normaliser;

    private final ChannelRef ref = new ChannelRef(UUID.randomUUID(), "clinical/deviation/x/pi-oversight");

    @Test
    void approvedDecisionMappsToDone() {
        var msg = new InboundHumanMessage("pi-001", "{\"decision\":\"APPROVED\",\"comment\":\"OK\"}", Instant.now(), Map.of());
        var result = normaliser.normalise(ref, msg);
        assertThat(result.type()).isEqualTo(MessageType.DONE);
        assertThat(result.senderInstanceId()).isEqualTo("human:pi-001");
        assertThat(result.content()).isEqualTo("{\"decision\":\"APPROVED\",\"comment\":\"OK\"}");
    }

    @Test
    void rejectedDecisionMapsToDecline() {
        var msg = new InboundHumanMessage("pi-001", "{\"decision\":\"REJECTED\"}", Instant.now(), Map.of());
        var result = normaliser.normalise(ref, msg);
        assertThat(result.type()).isEqualTo(MessageType.DECLINE);
    }

    @Test
    void unknownContentDefaultsToQuery() {
        var msg = new InboundHumanMessage("pi-001", "Hello, I have a question", Instant.now(), Map.of());
        var result = normaliser.normalise(ref, msg);
        assertThat(result.type()).isEqualTo(MessageType.QUERY);
    }

    // correlationId threading tests removed — NormalisedMessage in current artifact
    // has 3-param constructor only; restore when qhorus#154 artifact ships
}
