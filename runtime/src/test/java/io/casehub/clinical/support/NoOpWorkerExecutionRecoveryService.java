package io.casehub.clinical.support;

import io.casehub.engine.internal.model.CaseInstance;
import io.casehub.engine.spi.recovery.WorkerExecutionRecoveryService;
import io.quarkus.arc.DefaultBean;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.UUID;

/**
 * No-op test stub for {@link WorkerExecutionRecoveryService}.
 * Excluded engine bean {@code DefaultWorkerExecutionRecoveryService} requires a full
 * engine scheduler stack (WorkerExecutionManager + Quartz) not available in unit tests.
 * This stub satisfies the CDI graph for beans that inject the SPI without requiring
 * the full engine runtime.
 */
@ApplicationScoped
@DefaultBean
public class NoOpWorkerExecutionRecoveryService implements WorkerExecutionRecoveryService {

    @Override
    public Uni<CaseInstance> loadOrRestoreCaseInstance(UUID caseId) {
        return Uni.createFrom().nullItem();
    }

    @Override
    public Uni<Void> recoverPendingScheduledWorkers() {
        return Uni.createFrom().voidItem();
    }
}
