package io.casehub.clinical.ledger;

import io.casehub.ledger.runtime.model.jpa.JpaLedgerEntry;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Entity
@Table(name = "vital_sign_ledger_entry")
@DiscriminatorValue("VITAL_SIGN")
public class VitalSignLedgerEntry extends JpaLedgerEntry {

    @Column(name = "vital_sign_id")
    public UUID vitalSignId;

    @Column(name = "enrollment_id")
    public UUID enrollmentId;

    @Column(name = "vital_type")
    public String vitalType;

    @Column(name = "result_value")
    public BigDecimal resultValue;

    public String unit;

    @Override
    protected byte[] domainContentBytes() {
        return String.join("|",
                vitalSignId  != null ? vitalSignId.toString()  : "",
                enrollmentId != null ? enrollmentId.toString()  : "",
                vitalType    != null ? vitalType                : "",
                resultValue  != null ? resultValue.toPlainString() : "",
                unit         != null ? unit                     : "")
                .getBytes(StandardCharsets.UTF_8);
    }
}
