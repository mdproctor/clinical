package io.casehub.clinical.cbr;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.clinical.api.ClinicalActors;
import io.casehub.clinical.api.DsmbSafetySignalEvent;
import io.casehub.clinical.api.model.CtcaeGrade;
import io.casehub.clinical.entity.AdverseEvent;
import io.casehub.clinical.entity.ClinicalTrial;
import io.casehub.clinical.entity.TrialSafetySignal;
import io.casehub.clinical.entity.TrialSite;
import io.casehub.clinical.service.DsmbBatchSignalNotifier;
import io.casehub.neocortex.memory.cbr.FeatureValue;
import io.casehub.neocortex.memory.cbr.PlanCbrCase;
import io.casehub.platform.api.path.Path;
import io.casehub.work.api.WorkItemCreateRequest;
import io.casehub.work.api.WorkItemPriority;
import io.casehub.work.api.spi.WorkItemStore;
import io.casehub.work.runtime.service.WorkItemService;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@ApplicationScoped
public class TrialSafetyAggregationJob {

    private static final Logger LOG = Logger.getLogger(TrialSafetyAggregationJob.class);

    private final ClinicalCbrService cbrService;
    private final Clock clock;
    private final Event<DsmbSafetySignalEvent> signalEvent;
    private final WorkItemService workItemService;
    private final WorkItemStore workItemStore;
    private final DsmbBatchSignalNotifier dsmbNotifier;
    private final ObjectMapper objectMapper;

    @ConfigProperty(name = "casehub.clinical.trial-safety.tenant-id", defaultValue = "default")
    String tenantId;

    @ConfigProperty(name = "casehub.clinical.trial-safety.grade-threshold.min-grade", defaultValue = "3")
    int gradeThresholdMinGrade;

    @ConfigProperty(name = "casehub.clinical.trial-safety.grade-threshold.min-sites", defaultValue = "3")
    int gradeThresholdMinSites;

    @ConfigProperty(name = "casehub.clinical.trial-safety.grade-threshold.min-rate", defaultValue = "0.1")
    double gradeThresholdMinRate;

    @ConfigProperty(name = "casehub.clinical.trial-safety.cross-site-cluster.min-sites", defaultValue = "3")
    int crossSiteClusterMinSites;

    @ConfigProperty(name = "casehub.clinical.trial-safety.aggregation-period-days", defaultValue = "90")
    int aggregationPeriodDays;

    @ConfigProperty(name = "casehub.clinical.dsmb.batch-signal.sla", defaultValue = "PT72H")
    Duration batchSignalSla;

    @ConfigProperty(name = "casehub.clinical.dsmb.batch-signal.expiry", defaultValue = "P14D")
    Duration batchSignalExpiry;

    @Inject
    public TrialSafetyAggregationJob(ClinicalCbrService cbrService,
                                      Clock clock,
                                      Event<DsmbSafetySignalEvent> signalEvent,
                                      WorkItemService workItemService,
                                      WorkItemStore workItemStore,
                                      DsmbBatchSignalNotifier dsmbNotifier,
                                      ObjectMapper objectMapper) {
        this.cbrService = cbrService;
        this.clock = clock;
        this.signalEvent = signalEvent;
        this.workItemService = workItemService;
        this.workItemStore = workItemStore;
        this.dsmbNotifier = dsmbNotifier;
        this.objectMapper = objectMapper;
    }

    @Scheduled(every = "${casehub.clinical.trial-safety.interval:24h}",
               identity = "trial-safety-aggregation")
    public void aggregateAll() {
        List<ClinicalTrial> trials = QuarkusTransaction.requiringNew().call(() ->
            ClinicalTrial.<ClinicalTrial>list("tenantId = ?1 AND status = 'ACTIVE'", tenantId));

        int signalCount = 0;
        for (ClinicalTrial trial : trials) {
            try {
                signalCount += aggregateTrial(trial.id, trial.phase != null ? trial.phase.name() : "UNKNOWN");
            } catch (Exception e) {
                LOG.warnf(e, "Trial safety aggregation failed for trial %s — skipping", trial.id);
            }
        }
        if (signalCount > 0) {
            LOG.infof("Detected %d trial safety signals across %d trials", signalCount, trials.size());
        }
    }

    int aggregateTrial(UUID trialId, String trialPhase) {
        Instant cutoff = clock.instant().minus(aggregationPeriodDays, ChronoUnit.DAYS);

        Map<UUID, List<SiteAeSummary>> siteData = QuarkusTransaction.requiringNew().call(() -> {
            List<TrialSite> sites = TrialSite.<TrialSite>list("trialId = ?1 AND tenantId = ?2", trialId, tenantId);
            Map<UUID, List<SiteAeSummary>> result = new HashMap<>();
            for (TrialSite site : sites) {
                List<AdverseEvent> events = AdverseEvent.<AdverseEvent>list(
                    "enrollmentId IN (SELECT pe.id FROM PatientEnrollment pe WHERE pe.siteId = ?1 AND pe.tenantId = ?2) AND reportedAt >= ?3",
                    site.id, tenantId, cutoff);

                Map<String, Map<CtcaeGrade, Integer>> grouped = new HashMap<>();
                for (AdverseEvent ae : events) {
                    String et = ae.eventType != null ? ae.eventType : "UNKNOWN";
                    grouped.computeIfAbsent(et, k -> new HashMap<>())
                        .merge(ae.grade, 1, Integer::sum);
                }

                List<SiteAeSummary> summaries = new ArrayList<>();
                for (var entry : grouped.entrySet()) {
                    for (var gradeEntry : entry.getValue().entrySet()) {
                        summaries.add(new SiteAeSummary(gradeEntry.getKey(), entry.getKey(), gradeEntry.getValue()));
                    }
                }
                result.put(site.id, summaries);
            }
            return result;
        });

        List<DetectedSignal> signals = detectSignals(trialId, siteData, trialPhase, tenantId);

        for (DetectedSignal signal : signals) {
            storeCbrCase(trialId, signal, siteData.size(), trialPhase, tenantId);
            upsertSignalRecord(trialId, signal, tenantId);
            fireSignalEvent(trialId, signal, tenantId);
        }

        resolveStaleSignals(trialId, signals, tenantId);

        return signals.size();
    }

    List<DetectedSignal> detectSignals(UUID trialId,
                                        Map<UUID, List<SiteAeSummary>> siteData,
                                        String trialPhase, String tenantId) {
        List<DetectedSignal> signals = new ArrayList<>();

        detectGradeThreshold(siteData, signals);
        detectCrossSiteCluster(siteData, signals);

        return signals;
    }

    private void detectGradeThreshold(Map<UUID, List<SiteAeSummary>> siteData,
                                       List<DetectedSignal> signals) {
        List<UUID> affectedSites = new ArrayList<>();
        CtcaeGrade dominantGrade = null;
        String dominantEventType = null;
        int highestGradeCount = 0;

        for (var entry : siteData.entrySet()) {
            UUID siteId = entry.getKey();
            List<SiteAeSummary> summaries = entry.getValue();

            int totalCount = summaries.stream().mapToInt(SiteAeSummary::count).sum();
            int highGradeCount = summaries.stream()
                .filter(s -> s.grade().ordinal() + 1 >= gradeThresholdMinGrade)
                .mapToInt(SiteAeSummary::count)
                .sum();

            if (totalCount > 0 && (double) highGradeCount / totalCount >= gradeThresholdMinRate) {
                affectedSites.add(siteId);
                if (highGradeCount > highestGradeCount) {
                    highestGradeCount = highGradeCount;
                    dominantGrade = summaries.stream()
                        .filter(s -> s.grade().ordinal() + 1 >= gradeThresholdMinGrade)
                        .max((a, b) -> Integer.compare(a.count(), b.count()))
                        .map(SiteAeSummary::grade)
                        .orElse(CtcaeGrade.GRADE_3);
                    dominantEventType = summaries.stream()
                        .filter(s -> s.grade().ordinal() + 1 >= gradeThresholdMinGrade)
                        .max((a, b) -> Integer.compare(a.count(), b.count()))
                        .map(SiteAeSummary::eventType)
                        .orElse("UNKNOWN");
                }
            }
        }

        if (affectedSites.size() >= gradeThresholdMinSites) {
            String summary = "%d of %d sites show Grade %d+ AE rate above %.0f%%".formatted(
                affectedSites.size(), siteData.size(), gradeThresholdMinGrade, gradeThresholdMinRate * 100);
            signals.add(new DetectedSignal("GRADE_THRESHOLD", affectedSites, summary,
                dominantGrade, dominantEventType));
        }
    }

    private void detectCrossSiteCluster(Map<UUID, List<SiteAeSummary>> siteData,
                                         List<DetectedSignal> signals) {
        Map<String, List<UUID>> eventTypeSites = new HashMap<>();

        for (var entry : siteData.entrySet()) {
            for (SiteAeSummary summary : entry.getValue()) {
                eventTypeSites.computeIfAbsent(summary.eventType(), k -> new ArrayList<>())
                    .add(entry.getKey());
            }
        }

        for (var entry : eventTypeSites.entrySet()) {
            List<UUID> sites = entry.getValue().stream().distinct().collect(Collectors.toList());
            if (sites.size() >= crossSiteClusterMinSites) {
                CtcaeGrade maxGrade = siteData.values().stream()
                    .flatMap(List::stream)
                    .filter(s -> s.eventType().equals(entry.getKey()))
                    .map(SiteAeSummary::grade)
                    .max(CtcaeGrade::compareTo)
                    .orElse(CtcaeGrade.GRADE_1);

                String summary = "%s reported at %d sites (cluster threshold: %d)".formatted(
                    entry.getKey(), sites.size(), crossSiteClusterMinSites);
                signals.add(new DetectedSignal("CROSS_SITE_CLUSTER", sites, summary,
                    maxGrade, entry.getKey()));
            }
        }
    }

    void storeCbrCase(UUID trialId, DetectedSignal signal, int totalSiteCount,
                       String trialPhase, String tenantId) {
        Map<String, Object> rawFeatures = new LinkedHashMap<>();
        rawFeatures.put("trialPhase", trialPhase);
        rawFeatures.put("aggregationPeriodDays", aggregationPeriodDays);
        rawFeatures.put("siteCount", totalSiteCount);
        rawFeatures.put("affectedSiteCount", signal.affectedSites().size());
        rawFeatures.put("dominantGrade", signal.dominantGrade() != null ? signal.dominantGrade().ordinal() + 1 : 1);
        rawFeatures.put("dominantEventType", signal.dominantEventType() != null ? List.of(signal.dominantEventType()) : List.of());
        rawFeatures.put("signalType", signal.signalType());

        Map<String, FeatureValue> features = FeatureValue.toFeatureMap(rawFeatures);

        String entityId = "trial-" + trialId + "-" + signal.signalType().toLowerCase();
        PlanCbrCase cbrCase = new PlanCbrCase(
            "%s signal in %s trial: %s".formatted(signal.signalType(), trialPhase, signal.summary()),
            signal.summary(),
            "DETECTED", 1.0, features, List.of(),
            null, null);

        Path scope = Path.of(trialId.toString());
        cbrService.storeIdempotent(cbrCase, "clinical-trial-safety", entityId,
            ClinicalCbrDomains.TRIAL_SAFETY, tenantId, null, scope);
    }

    void upsertSignalRecord(UUID trialId, DetectedSignal signal, String tenantId) {
        // Phase 1: persist signal record
        UpsertResult result = QuarkusTransaction.requiringNew().call(() -> {
            TrialSafetySignal existing = TrialSafetySignal.findByTrialAndType(trialId, signal.signalType(), tenantId);
            Instant now = clock.instant();
            if (existing != null) {
                existing.affectedSiteCount = signal.affectedSites().size();
                existing.summary = signal.summary();
                existing.lastDetectedAt = now;
                existing.resolvedAt = null;
                boolean needsWorkItem = needsWorkItem(existing.workItemId);
                return new UpsertResult(existing.id, needsWorkItem);
            } else {
                TrialSafetySignal record = new TrialSafetySignal();
                record.id = UUID.randomUUID();
                record.tenantId = tenantId;
                record.trialId = trialId;
                record.signalType = signal.signalType();
                record.affectedSiteCount = signal.affectedSites().size();
                record.summary = signal.summary();
                record.firstDetectedAt = now;
                record.lastDetectedAt = now;
                record.persist();
                return new UpsertResult(record.id, true);
            }
        });

        // Phase 2: create WorkItem if needed (separate transaction for error isolation)
        if (result.needsWorkItem()) {
            try {
                UUID workItemId = QuarkusTransaction.requiringNew().call(() -> {
                    var wi = workItemService.create(WorkItemCreateRequest.builder()
                        .title("DSMB review — batch safety signal: " + signal.signalType())
                        .description(signal.summary() + ". Detected by trial safety aggregation job.")
                        .types(List.of("dsmb-batch-signal"))
                        .formKey("dsmb-batch-signal-review")
                        .priority(WorkItemPriority.HIGH)
                        .candidateGroups("dsmb")
                        .createdBy(ClinicalActors.CLINICAL_SERVICE)
                        .callerRef("clinical:trial-safety-signal/" + result.signalId())
                        .payload(buildWorkItemPayload(trialId, signal))
                        .claimDeadline(clock.instant().plus(batchSignalSla))
                        .expiresAt(clock.instant().plus(batchSignalExpiry))
                        .build());
                    TrialSafetySignal sig = TrialSafetySignal.findById(result.signalId());
                    if (sig != null) sig.workItemId = wi.id();
                    return wi.id();
                });
                dsmbNotifier.notify(trialId, signal.signalType(), signal.summary(),
                    signal.affectedSites().size(), workItemId);
            } catch (Exception e) {
                LOG.warnf(e, "WorkItem creation failed for trial %s signal %s — signal record persisted, WorkItem deferred to next run",
                    trialId, signal.signalType());
            }
        }
    }

    private boolean needsWorkItem(UUID existingWorkItemId) {
        if (existingWorkItemId == null) return true;
        return workItemStore.get(existingWorkItemId)
            .map(wi -> wi.status().isTerminal())
            .orElse(true);
    }

    private String buildWorkItemPayload(UUID trialId, DetectedSignal signal) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                "trialId", trialId.toString(),
                "signalType", signal.signalType(),
                "affectedSiteCount", signal.affectedSites().size(),
                "summary", signal.summary(),
                "affectedSites", signal.affectedSites().stream().map(UUID::toString).toList()));
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    record UpsertResult(UUID signalId, boolean needsWorkItem) {}

    void fireSignalEvent(UUID trialId, DetectedSignal signal, String tenantId) {
        signalEvent.fireAsync(new DsmbSafetySignalEvent(
            trialId, signal.signalType(), signal.affectedSites(), signal.summary(), tenantId));
    }

    private void resolveStaleSignals(UUID trialId, List<DetectedSignal> currentSignals, String tenantId) {
        var activeTypes = currentSignals.stream()
            .map(DetectedSignal::signalType)
            .collect(Collectors.toSet());

        QuarkusTransaction.requiringNew().run(() -> {
            List<TrialSafetySignal> active = TrialSafetySignal.findActiveByTrial(trialId, tenantId);
            Instant now = clock.instant();
            for (TrialSafetySignal record : active) {
                if (!activeTypes.contains(record.signalType)) {
                    record.resolvedAt = now;
                }
            }
        });
    }

    record SiteAeSummary(CtcaeGrade grade, String eventType, int count) {}

    record DetectedSignal(String signalType, List<UUID> affectedSites, String summary,
                           CtcaeGrade dominantGrade, String dominantEventType) {}
}
