package io.casehub.clinical.memory;

import io.casehub.platform.api.memory.MemoryDomain;

public final class ClinicalMemoryDomains {

    public static final MemoryDomain PATIENT = new MemoryDomain("clinical-patient");
    public static final MemoryDomain SITE    = new MemoryDomain("clinical-site");

    private ClinicalMemoryDomains() {}
}
