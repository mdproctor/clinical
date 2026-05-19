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
        var msg = new InboundHumanMessage("pi-001", "{\"decision\":\"APPROVED\",\"comment\":\"OK\"}", Instant.now(), Map.of("", ""), "");
        var result = normaliser.normalise(ref, msg);
        assertThat(result.type()).isEqualTo(MessageType.DONE);
        assertThat(result.senderInstanceId()).isEqualTo("human:pi-001");
        assertThat(result.content()).isEqualTo("{\"decision\":\"APPROVED\",\"comment\":\"OK\"}");
    }

    @Test
    void rejectedDecisionMapsToDecline() {
        var msg = new InboundHumanMessage("pi-001", "{\"decision\":\"REJECTED\"}", Instant.now(), Map.of("", ""), "");
        var result = normaliser.normalise(ref, msg);
        assertThat(result.type()).isEqualTo(MessageType.DECLINE);
    }

    @Test
    void unknownContentDefaultsToQuery() {
        var msg = new InboundHumanMessage("pi-001", "Hello, I have a question", Instant.now(), Map.of("", ""), "");
        var result = normaliser.normalise(ref, msg);
        assertThat(result.type()).isEqualTo(MessageType.QUERY);
    }

    @Test
    void oversightChannel_passesCorrelationIdThrough() {
        var msg = new InboundHumanMessage("pi-001", "{\"decision\":\"APPROVED\"}", Instant.now(), Map.of(), "dev-uuid-123");
        var result = normaliser.normalise(ref, msg);
        assertThat(result.correlationId()).isEqualTo("dev-uuid-123");
    }

    @Test
    void nonOversightChannel_passesCorrelationIdThrough() {
        var nonOversightRef = new ChannelRef(UUID.randomUUID(), "clinical/general");
        var msg = new InboundHumanMessage("pi-001", "hello", Instant.now(), Map.of(), "corr-456");
        var result = normaliser.normalise(nonOversightRef, msg);
        assertThat(result.correlationId()).isEqualTo("corr-456");
    }
}
