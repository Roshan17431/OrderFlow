package com.orderflow.order.config;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.Map;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(KafkaConfiguration.class);

    @Bean
    NewTopic orderEventsTopic(KafkaTopicProperties topics) {
        return TopicBuilder.name(topics.orderEvents()).partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic inventoryEventsTopic(KafkaTopicProperties topics) {
        return TopicBuilder.name(topics.inventoryEvents()).partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic paymentEventsTopic(KafkaTopicProperties topics) {
        return TopicBuilder.name(topics.paymentEvents()).partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic deliveryEventsTopic(KafkaTopicProperties topics) {
        return TopicBuilder.name(topics.deliveryEvents()).partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic orderEventsDltTopic(KafkaTopicProperties topics) {
        return TopicBuilder.name(topics.orderEventsDlt()).partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic inventoryEventsDltTopic(KafkaTopicProperties topics) {
        return TopicBuilder.name(topics.inventoryEventsDlt()).partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic paymentEventsDltTopic(KafkaTopicProperties topics) {
        return TopicBuilder.name(topics.paymentEventsDlt()).partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic deliveryEventsDltTopic(KafkaTopicProperties topics) {
        return TopicBuilder.name(topics.deliveryEventsDlt()).partitions(3).replicas(1).build();
    }

    @Bean
    DefaultErrorHandler orderKafkaErrorHandler(
            KafkaTemplate<String, String> template,
            KafkaTopicProperties topics,
            MeterRegistry metrics) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                template,
                (record, exception) -> {
                    metrics.counter(
                                    "orderflow_kafka_dlt_publications_total",
                                    "service",
                                    "order-service")
                            .increment();
                    String dlt = record.topic().equals(topics.paymentEvents())
                            ? topics.paymentEventsDlt()
                            : record.topic().equals(topics.deliveryEvents())
                                    ? topics.deliveryEventsDlt() : topics.inventoryEventsDlt();
                    LOGGER.error(
                            "Publishing invalid workflow event to DLT topic={} partition={} offset={}",
                            dlt,
                            record.partition(),
                            record.offset());
                    return new TopicPartition(dlt, record.partition());
                });
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, new FixedBackOff(500, 2));
        handler.setCommitRecovered(true);
        return handler;
    }

    @Bean
    HealthIndicator kafkaHealthIndicator(KafkaAdmin kafkaAdmin) {
        Map<String, Object> configuration = kafkaAdmin.getConfigurationProperties();
        return () -> {
            AdminClient client = AdminClient.create(configuration);
            try {
                client.listTopics().names().get(3, java.util.concurrent.TimeUnit.SECONDS);
                return Health.up().build();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return Health.down().build();
            } catch (Exception exception) {
                return Health.down().build();
            } finally {
                client.close(java.time.Duration.ZERO);
            }
        };
    }
}
