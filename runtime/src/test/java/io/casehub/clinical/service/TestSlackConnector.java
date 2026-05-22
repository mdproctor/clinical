package io.casehub.clinical.service;

import io.casehub.connectors.Connector;
import io.casehub.connectors.ConnectorMessage;
import io.quarkus.test.Mock;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

// @Singleton avoids CDI client proxy — @ApplicationScoped subclass-proxies the bean,
// so direct field access reads the proxy's own field (always empty), not the delegate's.
// @Singleton beans are injected directly; method calls and field access both hit the real instance.
//
// CopyOnWriteArrayList: SponsorNotificationIntegrationTest uses @ObservesAsync, which dispatches
// send() on a managed executor thread while the test thread reads via sent(). ArrayList has no
// JMM visibility guarantee across threads; COWAL provides safe publication without explicit sync.
@Mock
@Singleton
public class TestSlackConnector implements Connector {

    private final List<ConnectorMessage> sent = new CopyOnWriteArrayList<>();
    private boolean shouldThrow = false;

    public void reset() {
        sent.clear();
        shouldThrow = false;
    }

    public List<ConnectorMessage> sent() { return sent; }

    public void setShouldThrow(boolean value) { this.shouldThrow = value; }

    @Override
    public String id() { return "slack"; }

    @Override
    public void send(ConnectorMessage message) {
        if (shouldThrow) throw new RuntimeException("Connector failure");
        sent.add(message);
    }
}
