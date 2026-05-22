package io.casehub.clinical.ledger;

import io.casehub.ledger.runtime.model.LedgerEntry;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Tamper-evident record of AE escalation case completion.
 * Records safety review outcome and DSMB escalation status.
 * JOINED inheritance on qhorus datasource. V1010.
 * Must live in io.casehub.clinical.ledger — never in io.casehub.clinical.entity.
 */
@Entity
@Table(name = "ae_escalation_ledger_entry")
@DiscriminatorValue("AeEscalation")
public class AeEscalationLedgerEntry extends LedgerEntry {

    @Column(name = "ae_id", nullable = false)
    public UUID aeId;

    @Column(name = "enrollment_id", nullable = false)
    public UUID enrollmentId;

    @Column(name = "ctcae_grade", nullable = false)
    public String ctcaeGrade;

    @Column(name = "safety_review_outcome")
    public String safetyReviewOutcome;

    @Column(name = "dsmb_escalated", nullable = false)
    public boolean dsmbEscalated;

    @Column(name = "completed_at", nullable = false)
    public Instant completedAt;
}
