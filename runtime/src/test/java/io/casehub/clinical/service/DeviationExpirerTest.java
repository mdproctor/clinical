package io.casehub.clinical.service;

import io.casehub.clinical.api.model.*;
import io.casehub.clinical.entity.*;
import io.casehub.clinical.ledger.ProtocolDeviationLedgerEntry;
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
class DeviationExpirerTest {

    @Inject DeviationExpirer expirer;
    @Inject LedgerEntryRepository ledgerRepo;
    @Inject TestDeviationPersister persister;

    private UUID siteId;

    @BeforeAll
    @Transactional
    void setup() {
        UUID trialId = UUID.randomUUID();
        siteId = UUID.randomUUID();
        ClinicalTrial trial = new ClinicalTrial();
        trial.id = trialId;
        trial.protocolId = "EXPR";
        trial.phase = TrialPhase.PHASE_I;
        trial.sponsor = "S";
        trial.targetEnrollment = 5;
        trial.status = TrialStatus.ACTIVE;
        trial.persist();
        TrialSite site = new TrialSite();
        site.id = siteId;
        site.trialId = trialId;
        site.investigatorId = "pi-expr";
        site.persist();
    }

    @Test
    void expireOne_skipsNonExistentDeviation() {
        expirer.expireOne(UUID.randomUUID()); // must not throw
    }

    @Test
    void expireOne_skipsAlreadyTerminalDeviation() {
        UUID devId = persister.persistCommanded(siteId, DeviationSeverity.MINOR,
            EscalationRequirement.NONE, Instant.now().minus(1, ChronoUnit.DAYS));

        expirer.expireOne(devId); // first call — commits
        expirer.expireOne(devId); // second call — guard skips (EXPIRED ≠ COMMANDED)

        assertThat(ledgerRepo.findBySubjectId(devId)).hasSize(1); // not 2
    }

    @Test
    void expireOne_expiresCommandedDeviation() {
        UUID devId = persister.persistCommanded(siteId, DeviationSeverity.MINOR,
            EscalationRequirement.NONE, Instant.now().minus(1, ChronoUnit.DAYS));

        expirer.expireOne(devId);

        ProtocolDeviation loaded = ProtocolDeviation.findById(devId);
        assertThat(loaded.piApprovalStatus).isEqualTo(PiApprovalStatus.EXPIRED);

        var entries = ledgerRepo.findBySubjectId(devId);
        assertThat(entries).hasSize(1);
        assertThat(((ProtocolDeviationLedgerEntry) entries.get(0)).terminalStatus).isEqualTo("EXPIRED");
    }
}
