package io.casehub.clinical.service;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.UUID;

/**
 * Hourly scan that expires COMMANDED deviations whose responseDeadline has passed.
 *
 * <p>Each deviation is processed in an independent REQUIRES_NEW transaction via
 * {@link DeviationExpirer#expireOne(UUID)}. A failure expiring one deviation rolls back
 * only that deviation's sub-transaction — previously committed expirations are not affected.
 * Failed deviations remain COMMANDED and are retried on the next scheduled run.
 *
 * <p>The scheduler is disabled in tests ({@code quarkus.scheduler.enabled=false}).
 * Call {@link #checkExpiredCommitments()} directly in tests.
 */
@ApplicationScoped
public class DeviationExpirationJob {

    @Inject DeviationExpirer expirer;

    @Scheduled(every = "${casehub.clinical.deviation.expiration-check-interval:1h}",
               identity = "deviation-expiration")
    public void checkExpiredCommitments() {
        for (UUID id : expirer.findOverdueIds()) {
            try {
                expirer.expireOne(id);
            } catch (Exception e) {
                org.jboss.logging.Logger.getLogger(DeviationExpirationJob.class)
                    .errorf(e, "Failed to expire deviation %s — will retry next run", id);
            }
        }
    }
}
