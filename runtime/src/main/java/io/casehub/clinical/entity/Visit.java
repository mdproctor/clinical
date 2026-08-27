package io.casehub.clinical.entity;

import io.casehub.clinical.api.model.VisitStatus;
import io.casehub.clinical.api.model.VisitType;
import io.casehub.platform.api.identity.CurrentPrincipal;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import org.hibernate.annotations.DynamicUpdate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "visit")
@DynamicUpdate
public class Visit extends PanacheEntityBase {

    @Id
    public UUID id;

    @Column(name = "tenant_id", nullable = false)
    public String tenantId = "default";

    @Column(name = "enrollment_id", nullable = false)
    public UUID enrollmentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "visit_type", nullable = false)
    public VisitType visitType;

    @Column(name = "visit_date", nullable = false)
    public Instant visitDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public VisitStatus status;

    @Column(length = 2000)
    public String notes;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    public static Visit findByIdForTenant(UUID id, CurrentPrincipal principal) {
        Visit v = findById(id);
        if (v == null) return null;
        if (principal.isCrossTenantAdmin()) return v;
        return v.tenantId.equals(principal.tenancyId()) ? v : null;
    }

    public static List<Visit> listByEnrollment(UUID enrollmentId, String tenantId) {
        return list("enrollmentId = ?1 and tenantId = ?2", enrollmentId, tenantId);
    }
}
