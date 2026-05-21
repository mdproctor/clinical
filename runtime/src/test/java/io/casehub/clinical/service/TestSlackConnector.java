package io.casehub.clinical.service;

import io.casehub.connectors.Connector;
import io.casehub.connectors.ConnectorMessage;
import io.quarkus.test.Mock;
import java.util.ArrayList;
import java.util.List;

@Mock
public class TestSlackConnector implements Connector {

    public static final List<ConnectorMessage> sent = new ArrayList<>();
    public static boolean shouldThrow = false;

    @Override
    public String id() { return "slack"; }

    @Override
    public void send(ConnectorMessage message) {
        if (shouldThrow) throw new RuntimeException("Connector failure");
        sent.add(message);
    }
}
