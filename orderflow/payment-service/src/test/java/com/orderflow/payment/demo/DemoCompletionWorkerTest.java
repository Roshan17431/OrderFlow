package com.orderflow.payment.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import tools.jackson.databind.ObjectMapper;
import com.orderflow.payment.config.PaymentProperties;
import com.orderflow.payment.domain.Payment;
import com.orderflow.payment.domain.PaymentStatus;
import com.orderflow.payment.domain.Refund;
import com.orderflow.payment.provider.SimulatorPaymentProvider;
import com.orderflow.payment.repository.PaymentRepository;
import com.orderflow.payment.repository.RefundRepository;
import com.orderflow.payment.webhook.SimulatorWebhookVerifier;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class DemoCompletionWorkerTest {
    @Test
    void sendsSignedCallbacksThroughRealVerifierWithoutCustomerControlledAmount() throws Exception {
        var mapper = tools.jackson.databind.json.JsonMapper.builder().configureForJackson2().findAndAddModules().build();
        var clock = Clock.fixed(Instant.parse("2026-10-12T00:00:00Z"), ZoneOffset.UTC);
        var metrics = new SimpleMeterRegistry();
        var props = new PaymentProperties("simulator", true, false, "test-only-demo-webhook-secret", Duration.ofMinutes(5), Duration.ofSeconds(1), Duration.ofSeconds(2), 1, Duration.ofMillis(20), 100);
        var verifier = new SimulatorWebhookVerifier(props, new SimulatorPaymentProvider(mapper), metrics, clock);
        List<String> accepted = new ArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/payments/webhooks/simulator", exchange -> {
            byte[] payload = exchange.getRequestBody().readAllBytes();
            var headers = exchange.getRequestHeaders();
            var verified = verifier.verify(payload, headers.getFirst("X-Payment-Timestamp"), headers.getFirst("X-Payment-Signature"), headers.getFirst("X-Payment-Event-ID"));
            assertThat(mapper.readTree(payload).has("amount")).isFalse();
            accepted.add(verified.providerEventId()+":"+verified.event().eventType()+":"+verified.payloadHash());
            exchange.sendResponseHeaders(200, -1); exchange.close();
        });
        server.start();
        try {
            var payments = mock(PaymentRepository.class); var refunds = mock(RefundRepository.class);
            var p = Payment.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("10.00"), "USD", "SIMULATOR", "demo", clock.instant().plusSeconds(900), clock.instant());
            p.bindProviderPayment("sim_pay_"+p.getId(), clock.instant()); p.changeStatus(PaymentStatus.PROCESSING, clock.instant());
            var r = new Refund(p, new BigDecimal("2.00"), "DEMO", UUID.randomUUID(), clock.instant()); r.processing("sim_ref_"+r.getId(), clock.instant());
            when(payments.findDemoProcessing(any(), any())).thenReturn(List.of(p));
            when(refunds.findDemoProcessing(any())).thenReturn(List.of(r));
            var worker = new DemoCompletionWorker(payments, refunds, verifier, mapper, clock, metrics, server.getAddress().getPort());
            worker.complete(); worker.complete();
            assertThat(accepted).hasSize(4);
            assertThat(accepted.get(0)).isEqualTo(accepted.get(2)).contains("payment.succeeded");
            assertThat(accepted.get(1)).isEqualTo(accepted.get(3)).contains("refund.succeeded");
            assertThat(p.getStatus()).isEqualTo(PaymentStatus.PROCESSING);
            assertThat(r.getStatus().name()).isEqualTo("PROCESSING");
        } finally { server.stop(0); }
    }
    @Test
    void workerIsAbsentWithoutExplicitCloudDemoProfileOrWhenTrueProductionIsActive() {
        for (String profiles : new String[]{"local", "production,cloud-demo", "prod,cloud-demo"}) {
            new ApplicationContextRunner().withPropertyValues("spring.profiles.active="+profiles, "app.payment.demo-completion.enabled=true")
                    .withUserConfiguration(DemoCompletionWorker.class).run(context -> assertThat(context).doesNotHaveBean(DemoCompletionWorker.class));
        }
        new ApplicationContextRunner().withPropertyValues("spring.profiles.active=cloud-demo", "app.payment.demo-completion.enabled=false")
                .withUserConfiguration(DemoCompletionWorker.class).run(context -> assertThat(context).doesNotHaveBean(DemoCompletionWorker.class));
    }
}
