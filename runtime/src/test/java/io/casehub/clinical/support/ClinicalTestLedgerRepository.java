package io.casehub.clinical.support;

import io.casehub.ledger.runtime.model.LedgerAttestation;
import io.casehub.ledger.runtime.model.LedgerEntry;
import io.casehub.ledger.runtime.repository.LedgerEntryRepository;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Test-only LedgerEntryRepository implementation bridging the casehub-ledger snapshot API update
 * (single-arg → dual-arg with tenantId) while casehub-ledger-memory catches up.
 *
 * <p>tenantId parameters are ignored — tests run single-tenant. All entries are stored in a
 * shared ConcurrentHashMap. The bean is excluded from production augmentation via test scope.
 *
 * <p>Tracked for removal in casehubio/clinical#74 (ledger-memory SNAPSHOT sync).
 */
@Alternative
@Priority(2)
@ApplicationScoped
public class ClinicalTestLedgerRepository implements LedgerEntryRepository {

    private final ConcurrentHashMap<UUID, LedgerEntry> entries = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, AtomicInteger> sequenceCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, LedgerAttestation> attestations = new ConcurrentHashMap<>();

    @Override
    public LedgerEntry save(final LedgerEntry entry, final String tenantId) {
        if (entry.id == null) entry.id = UUID.randomUUID();
        if (entry.occurredAt == null) entry.occurredAt = Instant.now();
        entry.sequenceNumber = sequenceCounters
            .computeIfAbsent(entry.subjectId, k -> new AtomicInteger(0))
            .incrementAndGet();
        entries.put(entry.id, entry);
        return entry;
    }

    @Override
    public List<LedgerEntry> findBySubjectId(final UUID subjectId, final String tenantId) {
        return entries.values().stream()
            .filter(e -> subjectId.equals(e.subjectId))
            .sorted(Comparator.comparingInt(e -> e.sequenceNumber))
            .collect(Collectors.toList());
    }

    @Override
    public List<LedgerEntry> findBySubjectIdAndTimeRange(final UUID subjectId,
                                                         final Instant from, final Instant to,
                                                         final String tenantId) {
        return entries.values().stream()
            .filter(e -> subjectId.equals(e.subjectId))
            .filter(e -> !e.occurredAt.isBefore(from) && !e.occurredAt.isAfter(to))
            .sorted(Comparator.comparing(e -> e.occurredAt))
            .collect(Collectors.toList());
    }

    @Override
    public Optional<LedgerEntry> findLatestBySubjectId(final UUID subjectId, final String tenantId) {
        return entries.values().stream()
            .filter(e -> subjectId.equals(e.subjectId))
            .max(Comparator.comparingInt(e -> e.sequenceNumber));
    }

    @Override
    public Optional<LedgerEntry> findEntryById(final UUID entryId, final String tenantId) {
        return Optional.ofNullable(entries.get(entryId));
    }

    @Override
    public List<LedgerAttestation> findAttestationsByEntryId(final UUID entryId, final String tenantId) {
        return attestations.values().stream()
            .filter(a -> entryId.equals(a.ledgerEntryId))
            .collect(Collectors.toList());
    }

    @Override
    public LedgerAttestation saveAttestation(final LedgerAttestation attestation, final String tenantId) {
        if (attestation.id == null) attestation.id = UUID.randomUUID();
        if (attestation.occurredAt == null) attestation.occurredAt = Instant.now();
        attestations.put(attestation.id, attestation);
        return attestation;
    }

    @Override
    public List<LedgerEntry> findByActorId(final String actorId, final Instant from, final Instant to,
                                           final String tenantId) {
        return entries.values().stream()
            .filter(e -> actorId.equals(e.actorId))
            .filter(e -> !e.occurredAt.isBefore(from) && !e.occurredAt.isAfter(to))
            .collect(Collectors.toList());
    }

    @Override
    public List<LedgerEntry> findByActorRole(final String actorRole, final Instant from, final Instant to,
                                             final String tenantId) {
        return entries.values().stream()
            .filter(e -> actorRole.equals(e.actorRole))
            .filter(e -> !e.occurredAt.isBefore(from) && !e.occurredAt.isAfter(to))
            .collect(Collectors.toList());
    }

    @Override
    public List<LedgerEntry> findCausedBy(final UUID causedByEntryId, final String tenantId) {
        return entries.values().stream()
            .filter(e -> causedByEntryId.equals(e.causedByEntryId))
            .collect(Collectors.toList());
    }

    @Override
    public List<LedgerAttestation> findAttestationsByEntryIdAndCapabilityTag(final UUID entryId,
                                                                              final String capabilityTag,
                                                                              final String tenantId) {
        return attestations.values().stream()
            .filter(a -> entryId.equals(a.ledgerEntryId) && capabilityTag.equals(a.capabilityTag))
            .collect(Collectors.toList());
    }

    @Override
    public List<LedgerAttestation> findAttestationsByEntryIdGlobal(final UUID entryId, final String tenantId) {
        return findAttestationsByEntryIdAndCapabilityTag(entryId, "*", tenantId);
    }

    @Override
    public List<LedgerAttestation> findAttestationsByAttestorIdAndCapabilityTag(final String attestorId,
                                                                                 final String capabilityTag,
                                                                                 final String tenantId) {
        return attestations.values().stream()
            .filter(a -> attestorId.equals(a.attestorId) && capabilityTag.equals(a.capabilityTag))
            .collect(Collectors.toList());
    }
}
