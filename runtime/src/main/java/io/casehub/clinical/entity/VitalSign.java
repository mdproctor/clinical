package io.casehub.clinical.entity;

import io.casehub.clinical.api.model.VitalType;
import io.casehub.platform.api.identity.CurrentPrincipal;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import org.hibernate.annotations.DynamicUpdate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "vital_sign")
@DynamicUpdate
public class VitalSign extends PanacheEntityBase {

    @Id
    public UUID id;

    @Column(name = "tenant_id", nullable = false)
    public String tenantId = "default";

    @Column(name = "enrollment_id", nullable = false)
    public UUID enrollmentId;

    @Column(name = "visit_id")
    public UUID visitId;

    @Enumerated(EnumType.STRING)
    @Column(name = "vital_type", nullable = false, length = 50)
    public VitalType type;

    @Column(name = "result_value", nullable = false, precision = 19, scale = 4)
    public BigDecimal value;

    @Column(nullable = false, length = 50)
    public String unit;

    @Column(name = "measured_at", nullable = false)
    public Instant measuredAt;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    public static VitalSign findByIdForTenant(UUID id, CurrentPrincipal principal) {
        VitalSign vs = findById(id);
        if (vs == null) return null;
        if (principal.isCrossTenantAdmin()) return vs;
        return vs.tenantId.equals(principal.tenancyId()) ? vs : null;
    }

    public static List<VitalSign> listByEnrollment(UUID enrollmentId, String tenantId) {
        return list("enrollmentId = ?1 and tenantId = ?2", enrollmentId, tenantId);
    }
}
