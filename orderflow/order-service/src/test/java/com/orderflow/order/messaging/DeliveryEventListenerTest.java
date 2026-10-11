package com.orderflow.order.messaging;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderflow.order.service.OrderDeliveryWorkflowService;
import org.junit.jupiter.api.Test;

class DeliveryEventListenerTest {

    private final OrderDeliveryWorkflowService workflow = mock(OrderDeliveryWorkflowService.class);
    private final DeliveryEventListener listener = new DeliveryEventListener(new ObjectMapper(), workflow);

    @Test
    void ignoresDeliveryCommandsConsumedFromTheSharedTopic() throws Exception {
        listener.consume("not parsed for commands", "DELIVERY_REQUESTED");
        listener.consume("not parsed for commands", "DELIVERY_CANCEL_REQUESTED");

        verifyNoInteractions(workflow);
    }

    @Test
    void rejectsEventsThatDoNotBelongOnTheDeliveryTopic() {
        assertThatThrownBy(() -> listener.consume("{}", "ORDER_CREATED"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unsupported delivery event type");
    }
}
