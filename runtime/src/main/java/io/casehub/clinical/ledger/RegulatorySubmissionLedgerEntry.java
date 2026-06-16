package io.casehub.clinical.ledger;

import io.casehub.ledger.runtime.model.LedgerEntry;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

/**
 * Tamper-evident record for IND expedited safety report filing obligation.
 *
 * <p>Written in Phase 1 of RegulatorySubmissionCaseService when Grade 3 or Grade 5
 * + unexpected criteria are confirmed (21 CFR 312.32(c)(1)(i)/(c)(1)(ii)). EU AI Act Art.12 compliance supplement attached at write time.
 *
 * <p>JOINED inheritance on qhorus datasource. V2023.
 *
 * <p>{@code domainContentBytes()} uses aeId + grade only — both are stable identifiers
 * that survive any subsequent erasure or pseudonymization.
 */
@Entity
@Table(name = "regulatory_submission_ledger_entry")
@DiscriminatorValue("RegulatorySubmission")
public class RegulatorySubmissionLedgerEntry extends LedgerEntry {

    @Column(name = "ae_id", nullable = false)
    public UUID aeId;

    @Column(name = "ctcae_grade", nullable = false, length = 20)
    public String grade;

    @Column(name = "filed_at", nullable = false)
    public Instant filedAt;

    // aeId + grade + filedAt — all stable, erasure-safe identifiers (not PII, not subject to GDPR erasure).
    @Override
    protected byte[] domainContentBytes() {
        return String.join("|",
                aeId    != null ? aeId.toString()    : "",
                grade   != null ? grade              : "",
                filedAt != null ? filedAt.toString() : "")
                .getBytes(StandardCharsets.UTF_8);
    }
}
