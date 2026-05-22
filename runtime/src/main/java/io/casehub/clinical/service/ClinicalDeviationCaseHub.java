package io.casehub.clinical.service;

import io.casehub.api.engine.YamlCaseHub;
import jakarta.enterprise.context.ApplicationScoped;

/** Case definition for CRITICAL protocol deviation IRB gate (Layer 5). */
@ApplicationScoped
public class ClinicalDeviationCaseHub extends YamlCaseHub {

    public ClinicalDeviationCaseHub() {
        super("clinical/deviation-review.yaml");
    }
}
