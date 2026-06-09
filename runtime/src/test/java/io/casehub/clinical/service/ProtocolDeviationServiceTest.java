package io.casehub.clinical.service;

import io.casehub.clinical.api.model.DeviationSeverity;
import io.casehub.clinical.api.model.EscalationRequirement;
import io.casehub.clinical.api.model.PiApprovalStatus;
import io.casehub.clinical.api.model.TrialPhase;
import io.casehub.clinical.api.model.TrialStatus;
import io.casehub.clinical.entity.ClinicalTrial;
import io.casehub.clinical.entity.ProtocolDeviation;
import io.casehub.clinical.entity.TrialSite;
import io.casehub.clinical.ledger.ProtocolDeviationLedgerEntry;
import io.casehub.ledger.runtime.repository.LedgerEntryRepository;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.runtime.channel.ChannelService;
import io.casehub.qhorus.runtime.message.CommitmentService;
import io.casehub.qhorus.runtime.message.MessageService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProtocolDeviationServiceTest {

    @Inject ProtocolDeviationService service;
    @Inject ChannelService channelService;
    @Inject MessageService messageService;
    @Inject CommitmentService commitmentService;
    @Inject LedgerEntryRepository ledgerRepo;

    private UUID trialId;
    private UUID siteId;
    private UUID deviationId;

    @BeforeAll
    @Transactional
    void setup() {
        trialId = UUID.randomUUID();
        siteId = UUID.randomUUID();
        ClinicalTrial trial = new ClinicalTrial();
        trial.id = trialId;
        trial.protocolId = "PROT-001-" + trialId;
        trial.phase = TrialPhase.PHASE_II;
        trial.sponsor = "S";
        trial.targetEnrollment = 10;
        trial.status = TrialStatus.ACTIVE;
        trial.persist();
        TrialSite site = new TrialSite();
        site.id = siteId;
        site.trialId = trialId;
        site.investigatorId = "pi-001";
        site.persist();
    }

    @Test
    @Order(1)
    @Transactional
    void reportMinorDeviationSetsCommandedStateAndCreatesChannel() {
        ProtocolDeviation dev = new ProtocolDeviation();
        dev.id = UUID.randomUUID();
        deviationId = dev.id;
        dev.siteId = siteId;
        dev.deviationType = "sample-window";
        dev.severity = DeviationSeverity.MINOR;

        service.reportDeviation(dev);

        ProtocolDeviation loaded = ProtocolDeviation.findById(dev.id);
        assertThat(loaded.piApprovalStatus).isEqualTo(PiApprovalStatus.COMMANDED);
        assertThat(loaded.piCommandChannelName)
            .isEqualTo("clinical/deviation/dev-" + dev.id + "/pi-oversight");
        assertThat(loaded.commandedAt).isNotNull();
        assertThat(loaded.responseDeadline)
            .isAfter(Instant.now().plusSeconds(6 * 24 * 3600)); // > 6 days for MINOR
        assertThat(loaded.escalationRequirement).isEqualTo(EscalationRequirement.NONE);
    }

    @Test
    @Order(2)
    void channelExistsWithCorrectAllowedTypes() {
        assertThat(deviationId).as("deviationId set by Order(1)").isNotNull();
        var channel = channelService.findByName("clinical/deviation/dev-" + deviationId + "/pi-oversight");
        assertThat(channel).isPresent();
        assertThat(channel.get().allowedTypes).contains("COMMAND");
    }

    @Test
    @Order(3)
    void commandMessageInChannelWithCorrelationId() {
        assertThat(deviationId).as("deviationId set by Order(1)").isNotNull();
        var channel = channelService.findByName("clinical/deviation/dev-" + deviationId + "/pi-oversight").orElseThrow();
        var messages = messageService.pollAfter(channel.id, 0L, 10);
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).messageType).isEqualTo(MessageType.COMMAND);
        assertThat(messages.get(0).correlationId).isEqualTo(deviationId.toString());
        assertThat(messages.get(0).target).isEqualTo("pi-001");
    }

    @Test
    @Order(4)
    void commitmentIsOpenForDeviation() {
        assertThat(deviationId).as("deviationId set by Order(1)").isNotNull();
        var commitment = commitmentService.findByCorrelationId(deviationId.toString());
        assertThat(commitment).isPresent();
        assertThat(commitment.get().state.name()).isEqualTo("OPEN");
    }

    @Test
    @Order(5)
    @Transactional
    void ledgerEntryIsWritten() {
        assertThat(deviationId).as("deviationId set by Order(1)").isNotNull();
        var entries = ledgerRepo.findBySubjectId(deviationId, "default");
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0)).isInstanceOf(ProtocolDeviationLedgerEntry.class);
        ProtocolDeviationLedgerEntry entry = (ProtocolDeviationLedgerEntry) entries.get(0);
        assertThat(entry.deviationId).isEqualTo(deviationId);
        assertThat(entry.sequenceNumber).isEqualTo(1);
        assertThat(entry.terminalStatus).isNull();
        assertThat(entry.resolvedAt).isNull();
        assertThat(entry.actorId).isEqualTo("clinical-service");
    }

    @Test
    @Order(6)
    @Transactional
    void criticalDeviationGets24hDeadlineAndIrbEscalation() {
        ProtocolDeviation dev = new ProtocolDeviation();
        dev.id = UUID.randomUUID();
        dev.siteId = siteId;
        dev.deviationType = "eligibility-breach";
        dev.severity = DeviationSeverity.CRITICAL;

        service.reportDeviation(dev);

        ProtocolDeviation loaded = ProtocolDeviation.findById(dev.id);
        assertThat(loaded.escalationRequirement).isEqualTo(EscalationRequirement.IRB_REVIEW);
        assertThat(loaded.responseDeadline)
            .isBefore(Instant.now().plusSeconds(25 * 3600)); // < 25 hours for CRITICAL
    }
}
