package io.casehub.clinical.api.spi;

/**
 * Org-customisable policy for adverse event routing and engine case wiring.
 *
 * <p>The default implementation uses CTCAE v5.0 grades. Organisations override
 * this SPI to apply site-specific thresholds, team assignments, and scope rules.
 * This is a vocabulary SPI — a no-op default would break routing; the default
 * must express meaningful routing behaviour.
 */
public interface AdverseEventEscalationPolicy {
    AdverseEventEscalationRequirements evaluate(AdverseEventContext context);
}
