package io.casehub.clinical.service;

import io.casehub.clinical.api.SponsorNotificationRequest;
import io.casehub.clinical.api.SponsorNotifier;
import io.casehub.clinical.api.model.DeviationSeverity;
import io.casehub.clinical.api.model.PiApprovalStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * Unit test — verifies notify() delegates to store.createPending() and nothing else.
 * CDI resolution (DurableSponsorNotifier is the sole SponsorNotifier) is verified in
 * SponsorNotificationIntegrationTest.
 */
@ExtendWith(MockitoExtension.class)
class DurableSponsorNotifierTest {

    @Mock SponsorNotificationStore store;
    @InjectMocks DurableSponsorNotifier notifier;

    @Test
    void notify_delegates_to_store_createPending() {
        final SponsorNotificationRequest req = new SponsorNotificationRequest(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "CONSENT_DEVIATION", DeviationSeverity.MAJOR, PiApprovalStatus.ESCALATED,
                "dr-smith@v1", "Dr. Smith", "slack", "https://dest", "test-tenant");

        notifier.notify(req);

        verify(store).createPending(req);
        verifyNoMoreInteractions(store);
    }

    @Test
    void notifier_implements_SponsorNotifier_spi() {
        assertThat(notifier).isInstanceOf(SponsorNotifier.class);
    }
}
