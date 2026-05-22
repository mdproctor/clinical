package io.casehub.clinical.entity;

import io.casehub.clinical.api.model.IrbDecision;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "irb_approval")
public class IrbApproval extends PanacheEntityBase {

    @Id
    public UUID id;

    @Column(name = "site_id", nullable = false)
    public UUID siteId;

    /**
     * The deviation this IRB approval is for. Nullable for legacy stubs;
     * always set on new rows created by IrbDeviationCaseService.
     * Added in V109.
     */
    @Column(name = "deviation_id")
    public UUID deviationId;

    @Column(name = "review_type", nullable = false)
    public String reviewType;

    @Column(name = "committee_id", nullable = false)
    public String committeeId;

    @Column(name = "decision_deadline", nullable = false)
    public Instant decisionDeadline;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public IrbDecision decision = IrbDecision.PENDING;
}
