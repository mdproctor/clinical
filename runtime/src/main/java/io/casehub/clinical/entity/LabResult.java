package io.casehub.clinical.entity;

import io.casehub.clinical.api.model.AbnormalFlag;
import io.casehub.clinical.api.model.SpecimenType;
import io.casehub.platform.api.identity.CurrentPrincipal;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import org.hibernate.annotations.DynamicUpdate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "lab_result")
@DynamicUpdate
public class LabResult extends PanacheEntityBase {

    @Id
    public UUID id;

    @Column(name = "tenant_id", nullable = false)
    public String tenantId = "default";

    @Column(name = "enrollment_id", nullable = false)
    public UUID enrollmentId;

    @Column(name = "visit_id")
    public UUID visitId;

    @Column(name = "test_name", nullable = false)
    public String testName;

    @Column(name = "result_value", nullable = false, precision = 19, scale = 4)
    public BigDecimal value;

    @Column(nullable = false, length = 50)
    public String unit;

    @Column(name = "reference_range_low", precision = 19, scale = 4)
    public BigDecimal referenceRangeLow;

    @Column(name = "reference_range_high", precision = 19, scale = 4)
    public BigDecimal referenceRangeHigh;

    @Enumerated(EnumType.STRING)
    @Column(name = "abnormal_flag", nullable = false, length = 50)
    public AbnormalFlag abnormalFlag;

    @Enumerated(EnumType.STRING)
    @Column(name = "specimen_type", nullable = false, length = 50)
    public SpecimenType specimenType;

    @Column(name = "performing_lab")
    public String performingLab;

    @Column(name = "collected_at", nullable = false)
    public Instant collectedAt;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    public static LabResult findByIdForTenant(UUID id, CurrentPrincipal principal) {
        LabResult lr = findById(id);
        if (lr == null) return null;
        if (principal.isCrossTenantAdmin()) return lr;
        return lr.tenantId.equals(principal.tenancyId()) ? lr : null;
    }

    public static List<LabResult> listByEnrollment(UUID enrollmentId, String tenantId) {
        return list("enrollmentId = ?1 and tenantId = ?2", enrollmentId, tenantId);
    }
}
