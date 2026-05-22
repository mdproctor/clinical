package io.casehub.clinical.service;

import io.casehub.clinical.api.model.*;
import io.casehub.clinical.entity.*;
import io.casehub.clinical.ledger.ProtocolDeviationLedgerEntry;
import io.casehub.platform.api.identity.ActorType;
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
    @Inject TestDeviationPersister persister;

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
    void overdueCommandedDeviationIsMarkedExpired() {
        UUID devId = persister.persistCommanded(siteId, DeviationSeverity.MINOR,
            EscalationRequirement.NONE, Instant.now().minus(3, ChronoUnit.DAYS));

        job.checkExpiredCommitments();

        ProtocolDeviation loaded = ProtocolDeviation.findById(devId);
        assertThat(loaded.piApprovalStatus).isEqualTo(PiApprovalStatus.EXPIRED);

        var entries = ledgerRepo.findBySubjectId(devId);
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
    void twoOverdueDeviationsEachGetIndependentLedgerEntry() {
        UUID devId1 = persister.persistCommanded(siteId, DeviationSeverity.MINOR,
            EscalationRequirement.NONE, Instant.now().minus(1, ChronoUnit.DAYS));
        UUID devId2 = persister.persistCommanded(siteId, DeviationSeverity.MINOR,
            EscalationRequirement.NONE, Instant.now().minus(1, ChronoUnit.DAYS));

        job.checkExpiredCommitments();

        assertThat(ledgerRepo.findBySubjectId(devId1)).hasSize(1);
        assertThat(ledgerRepo.findBySubjectId(devId2)).hasSize(1);
    }

    @Test
    void futureDeadlineDeviationIsNotExpired() {
        UUID devId = persister.persistCommanded(siteId, DeviationSeverity.MINOR,
            EscalationRequirement.NONE, Instant.now().plus(7, ChronoUnit.DAYS));

        job.checkExpiredCommitments();

        ProtocolDeviation loaded = ProtocolDeviation.findById(devId);
        assertThat(loaded.piApprovalStatus).isEqualTo(PiApprovalStatus.COMMANDED);
    }
}
