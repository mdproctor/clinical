package io.casehub.clinical.service;

import io.casehub.clinical.api.model.DeviationSeverity;
import io.casehub.clinical.api.model.EscalationRequirement;
import io.casehub.clinical.api.model.PiApprovalStatus;
import io.casehub.clinical.entity.ProtocolDeviation;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@ApplicationScoped
class TestDeviationPersister {

    @Transactional
    UUID persistCommanded(UUID siteId, DeviationSeverity severity,
                          EscalationRequirement esc, Instant deadline) {
        ProtocolDeviation d = new ProtocolDeviation();
        d.id = UUID.randomUUID();
        d.siteId = siteId;
        d.deviationType = "test";
        d.severity = severity;
        d.piApprovalStatus = PiApprovalStatus.COMMANDED;
        d.escalationRequirement = esc;
        d.piCommandChannelName = "clinical/deviation/" + d.id + "/pi-oversight";
        d.commandedAt = Instant.now().minus(10, ChronoUnit.DAYS);
        d.responseDeadline = deadline;
        d.persist();
        return d.id;
    }
}
