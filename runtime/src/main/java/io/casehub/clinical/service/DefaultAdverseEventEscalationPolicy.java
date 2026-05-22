package io.casehub.clinical.service;

import io.casehub.clinical.api.spi.AdverseEventContext;
import io.casehub.clinical.api.spi.AdverseEventEscalationPolicy;
import io.casehub.clinical.api.spi.AdverseEventEscalationRequirements;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * CTCAE v5.0-based default escalation policy.
 *
 * <p>Grade 1-2: direct WorkItem creation to safety-officers (Layer 2 path preserved).
 * Grade 3: engine case — senior safety monitor gate.
 * Grade 4-5: engine case — senior safety monitor + DSMB escalation in parallel.
 *
 * <p>Organisations override this bean to apply their own thresholds and team assignments.
 */
@ApplicationScoped
@DefaultBean
public class DefaultAdverseEventEscalationPolicy implements AdverseEventEscalationPolicy {

    @Override
    public AdverseEventEscalationRequirements evaluate(AdverseEventContext context) {
        return switch (context.grade()) {
            case GRADE_1, GRADE_2 -> AdverseEventEscalationRequirements.direct("safety-officers");
            case GRADE_3          -> AdverseEventEscalationRequirements.engineManaged(true, false);
            case GRADE_4, GRADE_5 -> AdverseEventEscalationRequirements.engineManaged(true, true);
        };
    }
}
