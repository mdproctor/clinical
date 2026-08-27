package io.casehub.clinical.ledger;

import io.casehub.ledger.runtime.model.jpa.JpaLedgerEntry;
import jakarta.persistence.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "visit_ledger_entry")
@DiscriminatorValue("VISIT")
public class VisitLedgerEntry extends JpaLedgerEntry {

    @Column(name = "visit_id")
    public UUID visitId;

    @Column(name = "enrollment_id")
    public UUID enrollmentId;

    @Column(name = "visit_type")
    public String visitType;

    @Column(name = "visit_date")
    public Instant visitDate;

    @Column(name = "visit_status")
    public String visitStatus;

    @Override
    protected byte[] domainContentBytes() {
        return String.join("|",
                visitId      != null ? visitId.toString()      : "",
                enrollmentId != null ? enrollmentId.toString()  : "",
                visitType    != null ? visitType                : "",
                visitDate    != null ? visitDate.toString()     : "",
                visitStatus  != null ? visitStatus              : "")
                .getBytes(StandardCharsets.UTF_8);
    }
}
