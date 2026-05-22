package io.casehub.clinical.api.model;

/** IRB/ethics committee decision on a protocol deviation review or amendment. */
public enum IrbDecision {
    PENDING,
    APPROVED,
    REJECTED,
    /** Committee requests additional information before deciding. Not a final rejection. */
    DEFERRED,
    /** 72-hour IRB WorkItem expired before committee decided. */
    EXPIRED
}
