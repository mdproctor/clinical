package io.casehub.clinical.support;

import io.casehub.work.runtime.model.WorkItem;
import io.casehub.work.runtime.repository.WorkItemStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.List;

/**
 * Test-scope query helper. WorkItemStore.scanAll() requires a transaction;
 * this wrapper provides it so tests can call it from non-transactional contexts.
 */
@ApplicationScoped
public class WorkItemQueries {

    @Inject WorkItemStore store;

    @Transactional
    public List<WorkItem> scanAll() {
        return store.scanAll();
    }
}
