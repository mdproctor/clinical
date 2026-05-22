package io.casehub.clinical.service;

import io.casehub.clinical.api.model.*;
import io.casehub.clinical.entity.*;
import io.casehub.clinical.ledger.ProtocolDeviationLedgerEntry;
import io.casehub.platform.api.identity.ActorType;
import io.casehub.ledger.runtime.repository.LedgerEntryRepository;
import io.casehub.qhorus.api.message.MessageType;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PiResponseListenerTest {

    @Inject PiResponseListener listener;
    @Inject LedgerEntryRepository ledgerRepo;

    private UUID minorDeviationId, criticalDeviationId, rejectedDeviationId;

    @BeforeAll
    @Transactional
    void setup() {
        UUID trialId = UUID.randomUUID(), siteId = UUID.randomUUID();
        ClinicalTrial trial = new ClinicalTrial();
        trial.id = trialId; trial.protocolId = "P"; trial.phase = TrialPhase.PHASE_II;
        trial.sponsor = "S"; trial.targetEnrollment = 5; trial.status = TrialStatus.ACTIVE;
        trial.persist();
        TrialSite site = new TrialSite();
        site.id = siteId; site.trialId = trialId; site.investigatorId = "pi-L";
        site.persist();
        minorDeviationId = persistCommanded(siteId, DeviationSeverity.MINOR, EscalationRequirement.NONE);
        criticalDeviationId = persistCommanded(siteId, DeviationSeverity.CRITICAL, EscalationRequirement.IRB_REVIEW);
        rejectedDeviationId = persistCommanded(siteId, DeviationSeverity.MINOR, EscalationRequirement.NONE);
    }

    @Transactional
    UUID persistCommanded(UUID siteId, DeviationSeverity sev, EscalationRequirement esc) {
        ProtocolDeviation d = new ProtocolDeviation();
        d.id = UUID.randomUUID(); d.siteId = siteId; d.deviationType = "test"; d.severity = sev;
        d.piApprovalStatus = PiApprovalStatus.COMMANDED; d.escalationRequirement = esc;
        d.piCommandChannelName = "clinical/deviation/" + d.id + "/pi-oversight";
        d.commandedAt = Instant.now();
        d.responseDeadline = Instant.now().plus(24, ChronoUnit.HOURS);
        d.persist();
        return d.id;
    }

    @Test @Order(1)
    void approvedMinorDeviationSetsApproved() {
        listener.process("clinical/deviation/" + minorDeviationId + "/pi-oversight",
            MessageType.DONE, "human:pi-L");
        ProtocolDeviation loaded = ProtocolDeviation.findById(minorDeviationId);
        assertThat(loaded.piApprovalStatus).isEqualTo(PiApprovalStatus.APPROVED);
    }

    @Test @Order(2)
    void approvedCriticalDeviationSetsEscalated() {
        listener.process("clinical/deviation/" + criticalDeviationId + "/pi-oversight",
            MessageType.DONE, "human:pi-L");
        ProtocolDeviation loaded = ProtocolDeviation.findById(criticalDeviationId);
        assertThat(loaded.piApprovalStatus).isEqualTo(PiApprovalStatus.ESCALATED);
    }

    @Test @Order(3)
    void rejectedDeviationSetsRejected() {
        listener.process("clinical/deviation/" + rejectedDeviationId + "/pi-oversight",
            MessageType.DECLINE, "human:pi-L");
        ProtocolDeviation loaded = ProtocolDeviation.findById(rejectedDeviationId);
        assertThat(loaded.piApprovalStatus).isEqualTo(PiApprovalStatus.REJECTED);
    }

    @Test @Order(4)
    void nonMatchingChannelIsIgnored() {
        listener.process("clinical/other/channel", MessageType.DONE, "human:pi-L");
        // no exception, no state change — just verify it doesn't throw
    }

    @Test @Order(5)
    void alreadyTerminalDeviationIsIdempotent() {
        listener.process("clinical/deviation/" + minorDeviationId + "/pi-oversight",
            MessageType.DONE, "human:pi-L");
        ProtocolDeviation loaded = ProtocolDeviation.findById(minorDeviationId);
        assertThat(loaded.piApprovalStatus).isEqualTo(PiApprovalStatus.APPROVED);
    }

    @Test @Order(6)
    @Transactional
    void approvedMinorLedgerEntryWritten() {
        var entries = ledgerRepo.findBySubjectId(minorDeviationId);
        assertThat(entries).hasSize(1);
        ProtocolDeviationLedgerEntry entry = (ProtocolDeviationLedgerEntry) entries.get(0);
        assertThat(entry.terminalStatus).isEqualTo("APPROVED");
        assertThat(entry.actorId).isEqualTo("human:pi-L");
        assertThat(entry.actorType).isEqualTo(ActorType.HUMAN);
        assertThat(entry.actorRole).isEqualTo("pi-authoriser");
        assertThat(entry.resolvedAt).isNotNull();
        assertThat(entry.sequenceNumber).isEqualTo(1);
    }

    @Test @Order(7)
    @Transactional
    void escalatedCriticalLedgerEntryWritten() {
        var entries = ledgerRepo.findBySubjectId(criticalDeviationId);
        assertThat(entries).hasSize(1);
        ProtocolDeviationLedgerEntry entry = (ProtocolDeviationLedgerEntry) entries.get(0);
        assertThat(entry.terminalStatus).isEqualTo("ESCALATED");
        assertThat(entry.actorId).isEqualTo("human:pi-L");
    }

    @Test @Order(8)
    @Transactional
    void rejectedLedgerEntryWritten() {
        var entries = ledgerRepo.findBySubjectId(rejectedDeviationId);
        assertThat(entries).hasSize(1);
        ProtocolDeviationLedgerEntry entry = (ProtocolDeviationLedgerEntry) entries.get(0);
        assertThat(entry.terminalStatus).isEqualTo("REJECTED");
        assertThat(entry.actorId).isEqualTo("human:pi-L");
    }
}
