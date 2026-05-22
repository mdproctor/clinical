package io.casehub.clinical.ledger;

import io.casehub.ledger.runtime.model.LedgerEntry;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Tamper-evident ledger record for IRB committee decisions on protocol deviations.
 * FDA IND / GCP requirement: IRB approval or rejection must be independently
 * verifiable in the audit chain. JOINED inheritance on qhorus datasource. V1009.
 * Must live in io.casehub.clinical.ledger — never in io.casehub.clinical.entity.
 */
@Entity
@Table(name = "irb_approval_ledger_entry")
@DiscriminatorValue("IrbApproval")
public class IrbApprovalLedgerEntry extends LedgerEntry {

    @Column(name = "irb_approval_id", nullable = false)
    public UUID irbApprovalId;

    @Column(name = "deviation_id", nullable = false)
    public UUID deviationId;

    @Column(name = "irb_decision", nullable = false)
    public String irbDecision;

    @Column(name = "committee_id", nullable = false)
    public String committeeId;

    @Column(name = "decided_at", nullable = false)
    public Instant decidedAt;
}
