package io.casehub.clinical.service;

import io.casehub.clinical.api.model.CtcaeGrade;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.util.UUID;

@ApplicationScoped
public class AeEscalationLedgerWriter {
    public void writeCompletionEntry(UUID aeId, UUID enrollmentId, CtcaeGrade grade, String safetyReviewOutcome, boolean dsmbEscalated, Instant completedAt) {}
}
