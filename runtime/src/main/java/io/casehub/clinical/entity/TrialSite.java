package io.casehub.clinical.entity;

import io.casehub.clinical.api.model.SiteStatus;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "trial_site")
public class TrialSite extends PanacheEntityBase {

    @Id
    public UUID id;

    @Column(name = "tenant_id", nullable = false)
    public String tenantId = "default";

    @Column(name = "trial_id", nullable = false)
    public UUID trialId;

    @Column(name = "investigator_id", nullable = false)
    public String investigatorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public SiteStatus status = SiteStatus.PENDING;
}
