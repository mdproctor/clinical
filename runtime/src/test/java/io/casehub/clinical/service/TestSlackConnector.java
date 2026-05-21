package io.casehub.clinical.service;

import io.casehub.connectors.Connector;
import io.casehub.connectors.ConnectorMessage;
import io.quarkus.test.Mock;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Mock
public class TestSlackConnector implements Connector {

    public static final List<ConnectorMessage> sent = new CopyOnWriteArrayList<>();
    public static volatile boolean shouldThrow = false;

    @Override
    public String id() { return "slack"; }

    @Override
    public void send(ConnectorMessage message) {
        if (shouldThrow) throw new RuntimeException("Connector failure");
        sent.add(message);
    }
}
