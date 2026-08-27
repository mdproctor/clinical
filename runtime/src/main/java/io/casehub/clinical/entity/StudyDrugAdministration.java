package io.casehub.clinical.entity;

import io.casehub.clinical.api.model.DrugAdminStatus;
import io.casehub.clinical.api.model.MedicationRoute;
import io.casehub.platform.api.identity.CurrentPrincipal;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import org.hibernate.annotations.DynamicUpdate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "study_drug_administration")
@DynamicUpdate
public class StudyDrugAdministration extends PanacheEntityBase {

    @Id
    public UUID id;

    @Column(name = "tenant_id", nullable = false)
    public String tenantId = "default";

    @Column(name = "enrollment_id", nullable = false)
    public UUID enrollmentId;

    @Column(name = "visit_id")
    public UUID visitId;

    @Column(name = "drug_name", nullable = false)
    public String drugName;

    @Column(nullable = false, length = 100)
    public String dose;

    @Column(nullable = false, length = 50)
    public String unit;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    public MedicationRoute route;

    @Column(name = "administered_at", nullable = false)
    public Instant administeredAt;

    @Column(name = "administered_by", nullable = false)
    public String administeredBy;

    @Column(name = "batch_number")
    public String batchNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    public DrugAdminStatus status;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    public static StudyDrugAdministration findByIdForTenant(UUID id, CurrentPrincipal principal) {
        StudyDrugAdministration sda = findById(id);
        if (sda == null) return null;
        if (principal.isCrossTenantAdmin()) return sda;
        return sda.tenantId.equals(principal.tenancyId()) ? sda : null;
    }

    public static List<StudyDrugAdministration> listByEnrollment(UUID enrollmentId, String tenantId) {
        return list("enrollmentId = ?1 and tenantId = ?2", enrollmentId, tenantId);
    }
}
