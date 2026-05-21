package io.casehub.clinical.entity;

import io.casehub.clinical.api.model.TrialPhase;
import io.casehub.clinical.api.model.TrialStatus;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "clinical_trial")
public class ClinicalTrial extends PanacheEntityBase {

    @Id
    public UUID id;

    @Column(name = "protocol_id", nullable = false)
    public String protocolId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public TrialPhase phase;

    @Column(nullable = false)
    public String sponsor;

    @Column(name = "target_enrollment", nullable = false)
    public int targetEnrollment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public TrialStatus status = TrialStatus.PLANNING;

    @Column(name = "sponsor_notification_connector_id")
    public String sponsorNotificationConnectorId;

    @Column(name = "sponsor_notification_destination")
    public String sponsorNotificationDestination;
}
