package io.casehub.clinical.service;

import io.casehub.clinical.api.SafetyOfficerNotificationRequest;
import io.casehub.clinical.api.SafetyOfficerNotifier;
import io.casehub.clinical.api.model.CtcaeGrade;
import io.casehub.connectors.Connector;
import io.casehub.connectors.ConnectorMessage;
import io.quarkus.arc.All;
import io.quarkus.arc.DefaultBean;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@ApplicationScoped
@DefaultBean
public class DefaultSafetyOfficerNotifier implements SafetyOfficerNotifier {

    private final Map<String, Connector> connectorRegistry;
    private final SafetyOfficerNotificationLedgerWriter ledgerWriter;

    @Inject
    DefaultSafetyOfficerNotifier(
            @All final List<Connector> connectors,
            final SafetyOfficerNotificationLedgerWriter ledgerWriter) {
        this.connectorRegistry = connectors.stream()
            .collect(Collectors.toMap(Connector::id, Function.identity()));
        this.ledgerWriter = ledgerWriter;
    }

    @Override
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void notify(final SafetyOfficerNotificationRequest req) {
        final Connector connector = connectorRegistry.get(req.connectorId());
        if (connector == null) {
            Log.errorf("No connector '%s' found — safety officer notification not delivered for AE %s",
                req.connectorId(), req.aeId());
            writeLedgerEntry(req, false);
            return;
        }
        try {
            connector.send(new ConnectorMessage(
                req.destination(),
                buildTitle(req),
                buildBody(req)));
            writeLedgerEntry(req, true);
        } catch (Exception e) {
            Log.errorf(e, "Safety officer notification delivery failed for AE %s", req.aeId());
            writeLedgerEntry(req, false);
        }
    }

    private void writeLedgerEntry(final SafetyOfficerNotificationRequest req, final boolean delivered) {
        ledgerWriter.writeEntry(
            req.aeId(), req.enrollmentId(), req.siteId(), req.grade(),
            req.connectorId(), req.destination(), delivered);
    }

    private String buildTitle(final SafetyOfficerNotificationRequest req) {
        if (req.grade() == CtcaeGrade.GRADE_5) {
            return "[CRITICAL] " + req.grade().label() + " Adverse Event — aeId: " + req.aeId();
        }
        return "[" + req.grade().label() + " AE] — aeId: " + req.aeId();
    }

    private String buildBody(final SafetyOfficerNotificationRequest req) {
        return "Serious adverse event reported. " +
            "Grade: " + req.grade().label() + ". " +
            "AE ID: " + req.aeId() + ". " +
            "Enrollment ID: " + req.enrollmentId() + ". " +
            "Site ID: " + req.siteId() + ". " +
            "GCP SLA: " + req.grade().sla().orElseThrow().toHours() + "h from time of report.";
    }
}
