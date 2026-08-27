package io.casehub.clinical.ledger;

import io.casehub.ledger.runtime.model.jpa.JpaLedgerEntry;
import jakarta.persistence.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "concomitant_medication_ledger_entry")
@DiscriminatorValue("CONCOMITANT_MEDICATION")
public class ConcomitantMedicationLedgerEntry extends JpaLedgerEntry {

    @Column(name = "medication_id")
    public UUID medicationId;

    @Column(name = "enrollment_id")
    public UUID enrollmentId;

    @Column(name = "medication_name")
    public String medicationName;

    public String dose;
    public String unit;
    public String route;
    public String frequency;

    @Column(name = "start_date")
    public LocalDate startDate;

    @Override
    protected byte[] domainContentBytes() {
        return String.join("|",
                medicationId   != null ? medicationId.toString()   : "",
                enrollmentId   != null ? enrollmentId.toString()   : "",
                medicationName != null ? medicationName             : "",
                dose           != null ? dose                       : "",
                unit           != null ? unit                       : "",
                route          != null ? route                      : "",
                frequency      != null ? frequency                  : "",
                startDate      != null ? startDate.toString()      : "")
                .getBytes(StandardCharsets.UTF_8);
    }
}
