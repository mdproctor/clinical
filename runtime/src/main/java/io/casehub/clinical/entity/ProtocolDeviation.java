package io.casehub.clinical.entity;

import io.casehub.clinical.api.model.DeviationSeverity;
import io.casehub.clinical.api.model.EscalationRequirement;
import io.casehub.clinical.api.model.PiApprovalStatus;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "protocol_deviation")
public class ProtocolDeviation extends PanacheEntityBase {

    @Id
    public UUID id;

    @Column(name = "tenant_id", nullable = false)
    public String tenantId = "default";

    @Column(name = "site_id", nullable = false)
    public UUID siteId;

    @Column(name = "deviation_type", nullable = false)
    public String deviationType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public DeviationSeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(name = "pi_approval_status", nullable = false)
    public PiApprovalStatus piApprovalStatus = PiApprovalStatus.PENDING;

    @Column(name = "pi_command_channel_name")
    public String piCommandChannelName;

    @Column(name = "commanded_at")
    public Instant commandedAt;

    @Column(name = "response_deadline")
    public Instant responseDeadline;

    @Enumerated(EnumType.STRING)
    @Column(name = "escalation_requirement")
    public EscalationRequirement escalationRequirement;

    /** Links this CRITICAL deviation to its IRB review engine case. Null until IrbDeviationCaseService starts the case. */
    @Column(name = "engine_case_id")
    public UUID engineCaseId;
}
