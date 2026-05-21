package io.casehub.clinical.service;

import io.casehub.clinical.api.model.DeviationSeverity;
import io.casehub.clinical.api.model.EscalationRequirement;
import io.casehub.clinical.api.model.PiApprovalStatus;
import io.casehub.clinical.entity.ProtocolDeviation;
import io.casehub.clinical.ledger.ProtocolDeviationLedgerEntry;
import io.casehub.ledger.api.model.ActorType;
import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.runtime.model.LedgerEntry;
import io.casehub.ledger.runtime.repository.LedgerEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviationLedgerWriterTest {

    @Mock
    LedgerEntryRepository ledgerEntryRepository;

    @Mock
    Clock clock;

    @InjectMocks
    DeviationLedgerWriter writer;

    private static final Instant FIXED_INSTANT = Instant.parse("2026-05-19T10:00:00Z");
    private ProtocolDeviation dev;

    @BeforeEach
    void setUp() {
        lenient().when(clock.instant()).thenReturn(FIXED_INSTANT);
        dev = new ProtocolDeviation();
        dev.id = UUID.randomUUID();
        dev.siteId = UUID.randomUUID();
        dev.severity = DeviationSeverity.MINOR;
        dev.escalationRequirement = EscalationRequirement.NONE;
        dev.commandedAt = Instant.now();
        dev.responseDeadline = Instant.now().plusSeconds(3600);
        when(ledgerEntryRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void writeCommandEntry_sequenceNumber1WhenNoPriorEntries() {
        when(ledgerEntryRepository.findLatestBySubjectId(dev.id)).thenReturn(Optional.empty());

        writer.writeCommandEntry(dev, "pi-001");

        ProtocolDeviationLedgerEntry entry = captureEntry();
        assertThat(entry.sequenceNumber).isEqualTo(1);
    }

    @Test
    void writeCommandEntry_nullEscalationRequirementWritesNullColumn() {
        dev.escalationRequirement = null;
        when(ledgerEntryRepository.findLatestBySubjectId(dev.id)).thenReturn(Optional.empty());

        writer.writeCommandEntry(dev, "pi-001");

        ProtocolDeviationLedgerEntry entry = captureEntry();
        assertThat(entry.escalationRequirement).isNull();
    }

    @Test
    void writeCommandEntry_sequenceNumberIncrements() {
        LedgerEntry prior = new ProtocolDeviationLedgerEntry();
        prior.sequenceNumber = 2;
        when(ledgerEntryRepository.findLatestBySubjectId(dev.id)).thenReturn(Optional.of(prior));

        writer.writeCommandEntry(dev, "pi-001");

        ProtocolDeviationLedgerEntry entry = captureEntry();
        assertThat(entry.sequenceNumber).isEqualTo(3);
    }

    @Test
    void writeCommandEntry_setsCorrectFields() {
        when(ledgerEntryRepository.findLatestBySubjectId(dev.id)).thenReturn(Optional.empty());

        writer.writeCommandEntry(dev, "pi-001");

        ProtocolDeviationLedgerEntry entry = captureEntry();
        assertThat(entry.entryType).isEqualTo(LedgerEntryType.COMMAND);
        assertThat(entry.actorId).isEqualTo("clinical-service");
        assertThat(entry.actorType).isEqualTo(ActorType.SYSTEM);
        assertThat(entry.actorRole).isEqualTo("deviation-reporter");
        assertThat(entry.subjectId).isEqualTo(dev.id);
        assertThat(entry.deviationId).isEqualTo(dev.id);
        assertThat(entry.siteId).isEqualTo(dev.siteId);
        assertThat(entry.severity).isEqualTo("MINOR");
        assertThat(entry.piId).isEqualTo("pi-001");
        assertThat(entry.terminalStatus).isNull();
        assertThat(entry.resolvedAt).isNull();
        assertThat(entry.id).isNotNull();
    }

    @Test
    void writeResolutionEntry_approved_setsCorrectFields() {
        LedgerEntry prior = new ProtocolDeviationLedgerEntry();
        prior.sequenceNumber = 1;
        when(ledgerEntryRepository.findLatestBySubjectId(dev.id)).thenReturn(Optional.of(prior));

        writer.writeResolutionEntry(dev, PiApprovalStatus.APPROVED, "pi-actor-001", ActorType.HUMAN, "pi-authoriser");

        ProtocolDeviationLedgerEntry entry = captureEntry();
        assertThat(entry.entryType).isEqualTo(LedgerEntryType.EVENT);
        assertThat(entry.sequenceNumber).isEqualTo(2);
        assertThat(entry.actorId).isEqualTo("pi-actor-001");
        assertThat(entry.actorType).isEqualTo(ActorType.HUMAN);
        assertThat(entry.actorRole).isEqualTo("pi-authoriser");
        assertThat(entry.terminalStatus).isEqualTo("APPROVED");
        assertThat(entry.resolvedAt).isEqualTo(FIXED_INSTANT);
        assertThat(entry.occurredAt).isEqualTo(FIXED_INSTANT);
        assertThat(entry.subjectId).isEqualTo(dev.id);
    }

    @Test
    void writeResolutionEntry_escalated_setsTerminalStatus() {
        when(ledgerEntryRepository.findLatestBySubjectId(dev.id)).thenReturn(Optional.empty());

        writer.writeResolutionEntry(dev, PiApprovalStatus.ESCALATED, "pi-actor-001", ActorType.HUMAN, "pi-authoriser");

        ProtocolDeviationLedgerEntry entry = captureEntry();
        assertThat(entry.terminalStatus).isEqualTo("ESCALATED");
    }

    @Test
    void writeResolutionEntry_rejected_setsTerminalStatus() {
        when(ledgerEntryRepository.findLatestBySubjectId(dev.id)).thenReturn(Optional.empty());

        writer.writeResolutionEntry(dev, PiApprovalStatus.REJECTED, "pi-actor-001", ActorType.HUMAN, "pi-authoriser");

        ProtocolDeviationLedgerEntry entry = captureEntry();
        assertThat(entry.terminalStatus).isEqualTo("REJECTED");
        assertThat(entry.actorType).isEqualTo(ActorType.HUMAN);
    }

    @Test
    void writeResolutionEntry_expired_setsSystemActor() {
        when(ledgerEntryRepository.findLatestBySubjectId(dev.id)).thenReturn(Optional.empty());

        writer.writeResolutionEntry(dev, PiApprovalStatus.EXPIRED, "system", ActorType.SYSTEM, "deviation-expiration-job");

        ProtocolDeviationLedgerEntry entry = captureEntry();
        assertThat(entry.terminalStatus).isEqualTo("EXPIRED");
        assertThat(entry.actorId).isEqualTo("system");
        assertThat(entry.actorType).isEqualTo(ActorType.SYSTEM);
        assertThat(entry.actorRole).isEqualTo("deviation-expiration-job");
    }

    @Test
    void writeSponsorNotifiedEntry_sets_sponsor_notifier_role_and_notified_at_when_delivered() {
        when(ledgerEntryRepository.findLatestBySubjectId(dev.id))
            .thenReturn(Optional.of(existingEntry(2)));

        writer.writeSponsorNotifiedEntry(dev, FIXED_INSTANT, true);

        ProtocolDeviationLedgerEntry entry = captureEntry();
        assertThat(entry.actorRole).isEqualTo("sponsor-notifier");
        assertThat(entry.actorType).isEqualTo(ActorType.SYSTEM);
        assertThat(entry.entryType).isEqualTo(LedgerEntryType.EVENT);
        assertThat(entry.occurredAt).isEqualTo(FIXED_INSTANT);
        assertThat(entry.sponsorNotifiedAt).isEqualTo(FIXED_INSTANT);
        assertThat(entry.sequenceNumber).isEqualTo(3);
    }

    @Test
    void writeSponsorNotifiedEntry_sets_failed_role_and_null_notified_at_when_not_delivered() {
        when(ledgerEntryRepository.findLatestBySubjectId(dev.id))
            .thenReturn(Optional.empty());

        writer.writeSponsorNotifiedEntry(dev, FIXED_INSTANT, false);

        ProtocolDeviationLedgerEntry entry = captureEntry();
        assertThat(entry.actorRole).isEqualTo("sponsor-notifier-failed");
        assertThat(entry.actorType).isEqualTo(ActorType.SYSTEM);
        assertThat(entry.entryType).isEqualTo(LedgerEntryType.EVENT);
        assertThat(entry.sponsorNotifiedAt).isNull();
        assertThat(entry.sequenceNumber).isEqualTo(1);
    }

    private ProtocolDeviationLedgerEntry existingEntry(int seq) {
        ProtocolDeviationLedgerEntry e = new ProtocolDeviationLedgerEntry();
        e.sequenceNumber = seq;
        return e;
    }

    private ProtocolDeviationLedgerEntry captureEntry() {
        ArgumentCaptor<LedgerEntry> captor = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledgerEntryRepository).save(captor.capture());
        return (ProtocolDeviationLedgerEntry) captor.getValue();
    }
}
