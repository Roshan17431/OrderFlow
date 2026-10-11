package com.orderflow.delivery.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.orderflow.events.ShippingAddressSnapshot;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DeliveryDomainTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Test void completeLifecycleIsStrict() {
        Delivery delivery = delivery();
        UUID agent = UUID.randomUUID();
        delivery.assign(agent, NOW);
        delivery.pickup(NOW.plusSeconds(1));
        delivery.outForDelivery(NOW.plusSeconds(2));
        delivery.delivered(NOW.plusSeconds(3));
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.DELIVERED);
        assertThatThrownBy(() -> delivery.pickup(NOW.plusSeconds(4)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> delivery.cancel("REFUND", NOW.plusSeconds(4)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test void cancellationAndFailureFollowSafeRules() {
        Delivery pending = delivery();
        pending.cancel("FULL_REFUND", NOW);
        assertThat(pending.getStatus()).isEqualTo(DeliveryStatus.CANCELLED);
        Delivery active = delivery();
        active.assign(UUID.randomUUID(), NOW);
        active.failed("CUSTOMER_UNAVAILABLE", NOW.plusSeconds(1));
        assertThat(active.getStatus()).isEqualTo(DeliveryStatus.FAILED);
    }

    @Test void agentAvailabilityCannotForgeBusyOrAbandonActiveDelivery() {
        DeliveryAgentProfile agent = DeliveryAgentProfile.register(UUID.randomUUID(), NOW);
        agent.setUserAvailability(DeliveryAgentAvailability.AVAILABLE, NOW);
        agent.assigned(NOW);
        assertThat(agent.getAvailability()).isEqualTo(DeliveryAgentAvailability.BUSY);
        assertThatThrownBy(() -> agent.setUserAvailability(DeliveryAgentAvailability.OFFLINE, NOW))
                .isInstanceOf(IllegalStateException.class);
        agent.released(NOW);
        assertThat(agent.getAvailability()).isEqualTo(DeliveryAgentAvailability.AVAILABLE);
        assertThatThrownBy(() -> agent.setUserAvailability(DeliveryAgentAvailability.BUSY, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private Delivery delivery() {
        return Delivery.create(UUID.randomUUID(), UUID.randomUUID(),
                new ShippingAddressSnapshot("Recipient", "10 Street", null, "Kochi",
                        "Kerala", "682001", "IN", null), NOW);
    }
}
