package io.casehub.clinical.ledger;

import io.casehub.ledger.runtime.model.jpa.JpaLedgerEntry;
import jakarta.persistence.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Entity
@Table(name = "study_drug_ledger_entry")
@DiscriminatorValue("STUDY_DRUG")
public class StudyDrugLedgerEntry extends JpaLedgerEntry {

    @Column(name = "drug_admin_id")
    public UUID drugAdminId;

    @Column(name = "enrollment_id")
    public UUID enrollmentId;

    @Column(name = "drug_name")
    public String drugName;

    public String dose;
    public String unit;
    public String route;

    @Column(name = "administered_by")
    public String administeredBy;

    @Column(name = "drug_status")
    public String drugStatus;

    @Override
    protected byte[] domainContentBytes() {
        return String.join("|",
                drugAdminId    != null ? drugAdminId.toString()    : "",
                enrollmentId   != null ? enrollmentId.toString()   : "",
                drugName       != null ? drugName                   : "",
                dose           != null ? dose                       : "",
                unit           != null ? unit                       : "",
                route          != null ? route                      : "",
                administeredBy != null ? administeredBy             : "",
                drugStatus     != null ? drugStatus                 : "")
                .getBytes(StandardCharsets.UTF_8);
    }
}
