package io.casehub.clinical.service;

import io.casehub.clinical.api.model.*;
import io.casehub.clinical.entity.*;
import io.casehub.ledger.runtime.repository.LedgerEntryRepository;
import io.casehub.qhorus.runtime.message.CommitmentService;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.Optional;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Verifies that DeviationExpirer's REQUIRES_NEW isolation guarantee holds:
 * a JPA/application failure expiring deviation N must not roll back deviation N-1.
 */
@QuarkusTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DeviationExpirerIsolationTest {

    @Inject DeviationExpirationJob job;
    @InjectMock CommitmentService commitmentService;
    @Inject LedgerEntryRepository ledgerRepo;
    @Inject TestDeviationPersister persister;

    private UUID siteId;

    @BeforeAll
    @Transactional
    void setup() {
        UUID trialId = UUID.randomUUID();
        siteId = UUID.randomUUID();
        ClinicalTrial trial = new ClinicalTrial();
        trial.id = trialId; trial.protocolId = "ISO"; trial.phase = TrialPhase.PHASE_I;
        trial.sponsor = "S"; trial.targetEnrollment = 5; trial.status = TrialStatus.ACTIVE;
        trial.persist();
        TrialSite site = new TrialSite();
        site.id = siteId; site.trialId = trialId; site.investigatorId = "pi-iso";
        site.persist();
    }

    @Test
    void expirationFailureOnSecondDeviationDoesNotRollBackFirst() {
        UUID devId1 = persister.persistCommanded(siteId, DeviationSeverity.MINOR,
            EscalationRequirement.NONE, Instant.now().minus(1, ChronoUnit.DAYS));
        UUID devId2 = persister.persistCommanded(siteId, DeviationSeverity.MINOR,
            EscalationRequirement.NONE, Instant.now().minus(1, ChronoUnit.DAYS));

        // First call to fail() succeeds; second throws — simulating a failure on the second deviation
        when(commitmentService.fail(any()))
            .thenReturn(Optional.empty())
            .thenThrow(new RuntimeException("simulated failure on second deviation"));

        job.checkExpiredCommitments();

        ProtocolDeviation d1 = ProtocolDeviation.findById(devId1);
        ProtocolDeviation d2 = ProtocolDeviation.findById(devId2);

        // Exactly one deviation should be EXPIRED (the first one committed before the failure)
        long expiredCount = Stream.of(d1, d2)
            .filter(d -> d.piApprovalStatus == PiApprovalStatus.EXPIRED)
            .count();
        assertThat(expiredCount)
            .as("exactly one deviation should be EXPIRED — the first committed before the failure")
            .isEqualTo(1);

        // Exactly one ledger entry across both deviations
        int totalEntries = ledgerRepo.findBySubjectId(devId1).size()
            + ledgerRepo.findBySubjectId(devId2).size();
        assertThat(totalEntries)
            .as("exactly one EXPIRED ledger entry — the failed deviation's sub-transaction rolled back")
            .isEqualTo(1);
    }
}
