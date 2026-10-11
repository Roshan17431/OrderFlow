package com.orderflow.delivery.outbox;import java.util.UUID;public record OutboxMessage(UUID id,String topic,String messageKey,String eventType,int eventVersion,String payload,int attempts){}
