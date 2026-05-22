package io.casehub.clinical.service;

import io.casehub.api.engine.YamlCaseHub;
import jakarta.enterprise.context.ApplicationScoped;

/** Case definition for Grade 3+ adverse event safety escalation (Layer 5). */
@ApplicationScoped
public class ClinicalAdverseEventCaseHub extends YamlCaseHub {

    public ClinicalAdverseEventCaseHub() {
        super("clinical/ae-escalation.yaml");
    }
}
