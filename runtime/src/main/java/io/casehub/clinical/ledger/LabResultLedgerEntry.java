package io.casehub.clinical.ledger;

import io.casehub.ledger.runtime.model.jpa.JpaLedgerEntry;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Entity
@Table(name = "lab_result_ledger_entry")
@DiscriminatorValue("LAB_RESULT")
public class LabResultLedgerEntry extends JpaLedgerEntry {

    @Column(name = "lab_result_id")
    public UUID labResultId;

    @Column(name = "enrollment_id")
    public UUID enrollmentId;

    @Column(name = "test_name")
    public String testName;

    @Column(name = "result_value")
    public BigDecimal resultValue;

    public String unit;

    @Column(name = "abnormal_flag")
    public String abnormalFlag;

    @Column(name = "specimen_type")
    public String specimenType;

    @Override
    protected byte[] domainContentBytes() {
        return String.join("|",
                labResultId  != null ? labResultId.toString()  : "",
                enrollmentId != null ? enrollmentId.toString()  : "",
                testName     != null ? testName                 : "",
                resultValue  != null ? resultValue.toPlainString() : "",
                unit         != null ? unit                     : "",
                abnormalFlag != null ? abnormalFlag              : "",
                specimenType != null ? specimenType              : "")
                .getBytes(StandardCharsets.UTF_8);
    }
}
