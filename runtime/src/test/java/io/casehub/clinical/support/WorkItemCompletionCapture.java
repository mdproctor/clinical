package io.casehub.clinical.support;

import io.casehub.work.runtime.event.WorkItemLifecycleEvent;
import io.casehub.work.runtime.model.WorkItemStatus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Test-scope bean that captures async WorkItem lifecycle events.
 * Used to verify @ObservesAsync CDI delivery to in-process observers
 * before manually invoking WorkItemLifecycleAdapter (engine#315).
 */
@ApplicationScoped
public class WorkItemCompletionCapture {

    private final ConcurrentMap<UUID, WorkItemLifecycleEvent> completed = new ConcurrentHashMap<>();

    void onCompleted(@ObservesAsync WorkItemLifecycleEvent event) {
        if (event.status() == WorkItemStatus.COMPLETED) {
            completed.put(event.workItemId(), event);
        }
    }

    public boolean wasCompleted(UUID workItemId) {
        return completed.containsKey(workItemId);
    }

    public void reset() {
        completed.clear();
    }
}
