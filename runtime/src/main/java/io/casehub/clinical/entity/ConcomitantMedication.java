package io.casehub.clinical.entity;

import io.casehub.clinical.api.model.MedicationFrequency;
import io.casehub.clinical.api.model.MedicationRoute;
import io.casehub.platform.api.identity.CurrentPrincipal;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import org.hibernate.annotations.DynamicUpdate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "concomitant_medication")
@DynamicUpdate
public class ConcomitantMedication extends PanacheEntityBase {

    @Id
    public UUID id;

    @Column(name = "tenant_id", nullable = false)
    public String tenantId = "default";

    @Column(name = "enrollment_id", nullable = false)
    public UUID enrollmentId;

    @Column(name = "medication_name", nullable = false)
    public String medicationName;

    @Column(length = 500)
    public String indication;

    @Column(nullable = false, length = 100)
    public String dose;

    @Column(nullable = false, length = 50)
    public String unit;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    public MedicationRoute route;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    public MedicationFrequency frequency;

    @Column(name = "start_date", nullable = false)
    public LocalDate startDate;

    @Column(name = "end_date")
    public LocalDate endDate;

    @Column(nullable = false)
    public boolean ongoing = true;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    public static ConcomitantMedication findByIdForTenant(UUID id, CurrentPrincipal principal) {
        ConcomitantMedication cm = findById(id);
        if (cm == null) return null;
        if (principal.isCrossTenantAdmin()) return cm;
        return cm.tenantId.equals(principal.tenancyId()) ? cm : null;
    }

    public static List<ConcomitantMedication> listByEnrollment(UUID enrollmentId, String tenantId) {
        return list("enrollmentId = ?1 and tenantId = ?2", enrollmentId, tenantId);
    }
}
