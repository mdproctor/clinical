package io.casehub.clinical.ledger;

import io.casehub.ledger.runtime.model.LedgerEntry;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Tamper-evident audit record for the full PI authorisation lifecycle of a protocol deviation.
 *
 * Extends LedgerEntry via JPA JOINED inheritance — base fields (subjectId,
 * sequenceNumber, actorId, occurredAt, digest) are in the ledger_entry table;
 * deviation-specific fields are in protocol_deviation_ledger_entry (V1006 migration);
 * resolution fields (terminalStatus, resolvedAt) were added in V1007.
 *
 * GCP / ICH E6(R3) requirement: every protocol deviation must have a named PI
 * commitment with a tamper-evident record of the command, the deadline, and how
 * it was resolved (APPROVED, REJECTED, ESCALATED, or EXPIRED). The sequenceNumber
 * chain within a subjectId provides a complete, ordered deviation audit trail.
 *
 * Written by DeviationLedgerWriter. COMMAND entries leave terminalStatus/resolvedAt null;
 * resolution entries (EVENT type) populate them.
 */
@Entity
@Table(name = "protocol_deviation_ledger_entry")
@DiscriminatorValue("PROTOCOL_DEVIATION")
public class ProtocolDeviationLedgerEntry extends LedgerEntry {

    @Column(name = "deviation_id")
    public UUID deviationId;

    @Column(name = "site_id")
    public UUID siteId;

    public String severity;

    @Column(name = "pi_id")
    public String piId;

    @Column(name = "commanded_at")
    public Instant commandedAt;

    @Column(name = "response_deadline")
    public Instant responseDeadline;

    @Column(name = "escalation_requirement")
    public String escalationRequirement;

    @Column(name = "terminal_status")
    public String terminalStatus;

    @Column(name = "resolved_at")
    public Instant resolvedAt;

    @Column(name = "sponsor_notified_at")
    public Instant sponsorNotifiedAt;
}
