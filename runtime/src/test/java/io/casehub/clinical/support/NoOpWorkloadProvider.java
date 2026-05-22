package io.casehub.clinical.support;

import io.casehub.work.api.WorkloadProvider;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * No-op test stub for {@link WorkloadProvider}.
 * The engine's {@code CasehubWorkloadProvider} is excluded (requires engine scheduler stack).
 * {@code JpaWorkloadProvider} is excluded (CDI ambiguity with engine bridge).
 * This stub satisfies {@link io.casehub.work.runtime.service.WorkItemAssignmentService}'s
 * injection point without requiring any external dependencies.
 */
@ApplicationScoped
@DefaultBean
public class NoOpWorkloadProvider implements WorkloadProvider {

    @Override
    public int getActiveWorkCount(String candidateGroup) {
        return 0;
    }
}
