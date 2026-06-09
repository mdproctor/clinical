package io.casehub.clinical.entity;

import io.casehub.clinical.api.model.AeEscalationStatus;
import io.casehub.clinical.api.model.AeOutcome;
import io.casehub.clinical.api.model.CtcaeGrade;
import io.casehub.clinical.api.model.EventActuality;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "adverse_event")
public class AdverseEvent extends PanacheEntityBase {

    @Id
    public UUID id;

    @Column(name = "tenant_id", nullable = false)
    public String tenantId = "default";

    @Column(name = "enrollment_id", nullable = false)
    public UUID enrollmentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public CtcaeGrade grade;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public EventActuality actuality = EventActuality.ACTUAL;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public AeOutcome outcome = AeOutcome.ONGOING;

    @Column(name = "occurred_at", nullable = false)
    public Instant occurredAt;

    @Column(name = "reported_at", nullable = false)
    public Instant reportedAt;

    /** Computed as reportedAt + grade.sla(). Present for all grades per GCP ICH E6(R3) §5.17. */
    @Column(name = "sla_deadline")
    public Instant slaDeadline;

    /** WorkItem id created by AdverseEventService for GCP SLA tracking. Null until service call. */
    @Column(name = "work_item_id")
    public UUID workItemId;

    @Enumerated(EnumType.STRING)
    @Column(name = "escalation_status", nullable = false)
    public AeEscalationStatus escalationStatus = AeEscalationStatus.NONE;

    @Column(name = "engine_case_id")
    public UUID engineCaseId;
}
