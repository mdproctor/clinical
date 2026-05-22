package io.casehub.clinical.api.spi;

/**
 * Policy decision for a reported adverse event.
 *
 * <p>When {@code engineCaseRequired} is false, {@code candidateGroups} is used to
 * create a WorkItem directly (Layer 2 path). When true, {@code candidateGroups} is
 * null and the engine case creates WorkItems via humanTask bindings using
 * {@code requiresSeniorMonitor} and {@code requiresDsmbEscalation} as context keys.
 */
public record AdverseEventEscalationRequirements(
    boolean engineCaseRequired,
    String candidateGroups,
    boolean requiresSeniorMonitor,
    boolean requiresDsmbEscalation) {

    public static AdverseEventEscalationRequirements direct(String candidateGroups) {
        return new AdverseEventEscalationRequirements(false, candidateGroups, false, false);
    }

    public static AdverseEventEscalationRequirements engineManaged(
            boolean requiresSeniorMonitor, boolean requiresDsmbEscalation) {
        return new AdverseEventEscalationRequirements(
                true, null, requiresSeniorMonitor, requiresDsmbEscalation);
    }
}
