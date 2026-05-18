package io.casehub.clinical.service;

import io.casehub.clinical.api.model.*;
import io.casehub.clinical.entity.*;
import io.casehub.clinical.ledger.ProtocolDeviationLedgerEntry;
import io.casehub.ledger.api.model.ActorType;
import io.casehub.ledger.runtime.repository.LedgerEntryRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DeviationExpirationJobTest {

    @Inject DeviationExpirationJob job;
    @Inject LedgerEntryRepository ledgerRepo;

    private UUID siteId;

    @BeforeAll
    @Transactional
    void setup() {
        UUID trialId = UUID.randomUUID();
        siteId = UUID.randomUUID();
        ClinicalTrial trial = new ClinicalTrial();
        trial.id = trialId; trial.protocolId = "EXP"; trial.phase = TrialPhase.PHASE_I;
        trial.sponsor = "S"; trial.targetEnrollment = 5; trial.status = TrialStatus.ACTIVE;
        trial.persist();
        TrialSite site = new TrialSite();
        site.id = siteId; site.trialId = trialId; site.investigatorId = "pi-exp";
        site.persist();
    }

    @Test
    @Transactional
    void overdueCommandedDeviationIsMarkedExpired() {
        ProtocolDeviation dev = new ProtocolDeviation();
        dev.id = UUID.randomUUID();
        dev.siteId = siteId;
        dev.deviationType = "overdue"; dev.severity = DeviationSeverity.MINOR;
        dev.piApprovalStatus = PiApprovalStatus.COMMANDED;
        dev.escalationRequirement = EscalationRequirement.NONE;
        dev.piCommandChannelName = "clinical/deviation/" + dev.id + "/pi-oversight";
        dev.commandedAt = Instant.now().minus(10, ChronoUnit.DAYS);
        dev.responseDeadline = Instant.now().minus(3, ChronoUnit.DAYS);
        dev.persist();

        job.checkExpiredCommitments();

        ProtocolDeviation loaded = ProtocolDeviation.findById(dev.id);
        assertThat(loaded.piApprovalStatus).isEqualTo(PiApprovalStatus.EXPIRED);

        var entries = ledgerRepo.findBySubjectId(dev.id);
        assertThat(entries).hasSize(1);
        ProtocolDeviationLedgerEntry entry = (ProtocolDeviationLedgerEntry) entries.get(0);
        assertThat(entry.terminalStatus).isEqualTo("EXPIRED");
        assertThat(entry.actorId).isEqualTo("system");
        assertThat(entry.actorType).isEqualTo(ActorType.SYSTEM);
        assertThat(entry.actorRole).isEqualTo("deviation-expiration-job");
        assertThat(entry.resolvedAt).isNotNull();
        assertThat(entry.sequenceNumber).isEqualTo(1);
    }

    @Test
    @Transactional
    void twoOverdueDeviationsEachGetIndependentLedgerEntry() {
        UUID devId1 = UUID.randomUUID(), devId2 = UUID.randomUUID();
        for (UUID id : new UUID[]{devId1, devId2}) {
            ProtocolDeviation d = new ProtocolDeviation();
            d.id = id; d.siteId = siteId;
            d.deviationType = "overdue"; d.severity = DeviationSeverity.MINOR;
            d.piApprovalStatus = PiApprovalStatus.COMMANDED;
            d.escalationRequirement = EscalationRequirement.NONE;
            d.piCommandChannelName = "clinical/deviation/" + id + "/pi-oversight";
            d.commandedAt = Instant.now().minus(10, ChronoUnit.DAYS);
            d.responseDeadline = Instant.now().minus(1, ChronoUnit.DAYS);
            d.persist();
        }

        job.checkExpiredCommitments();

        assertThat(ledgerRepo.findBySubjectId(devId1)).hasSize(1);
        assertThat(ledgerRepo.findBySubjectId(devId2)).hasSize(1);
    }

    @Test
    @Transactional
    void futureDeadlineDeviationIsNotExpired() {
        ProtocolDeviation dev = new ProtocolDeviation();
        dev.id = UUID.randomUUID();
        dev.siteId = siteId;
        dev.deviationType = "active"; dev.severity = DeviationSeverity.MINOR;
        dev.piApprovalStatus = PiApprovalStatus.COMMANDED;
        dev.escalationRequirement = EscalationRequirement.NONE;
        dev.piCommandChannelName = "clinical/deviation/" + dev.id + "/pi-oversight";
        dev.commandedAt = Instant.now();
        dev.responseDeadline = Instant.now().plus(7, ChronoUnit.DAYS);
        dev.persist();

        job.checkExpiredCommitments();

        ProtocolDeviation loaded = ProtocolDeviation.findById(dev.id);
        assertThat(loaded.piApprovalStatus).isEqualTo(PiApprovalStatus.COMMANDED);
    }
}
