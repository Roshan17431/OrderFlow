package com.orderflow.delivery.dto;import jakarta.validation.constraints.NotNull;import java.util.UUID;public record AssignDeliveryRequest(@NotNull UUID deliveryAgentId){}
