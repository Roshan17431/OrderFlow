package com.orderflow.notification.service;

import com.orderflow.notification.config.NotificationProperties;
import com.orderflow.notification.dto.NotificationResponse;
import io.micrometer.core.instrument.*;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
public class SseRegistry {
    private static final int MAX_PER_USER = 5;
    private final Map<UUID, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();
    private final NotificationProperties properties;
    private final Counter failures;
    private final AtomicInteger count = new AtomicInteger();

    public SseRegistry(NotificationProperties p, MeterRegistry m) {
        properties = p;
        failures = m.counter("notification_sse_send_failures_total");
        Gauge.builder("notification_sse_connections", count, AtomicInteger::get).register(m);
    }

    public SseEmitter connect(UUID user) {
        SseEmitter emitter = new SseEmitter(properties.sseTimeout().toMillis());
        Runnable cleanup = () -> remove(user, emitter);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(ignored -> cleanup.run());
        SseEmitter evicted = null;
        synchronized (this) {
            var list = emitters.computeIfAbsent(user, ignored -> new CopyOnWriteArrayList<>());
            if (list.size() >= MAX_PER_USER) {
                evicted = list.removeFirst();
                count.decrementAndGet();
            }
            list.add(emitter);
            count.incrementAndGet();
        }
        if (evicted != null) evicted.complete();
        try {
            emitter.send(SseEmitter.event().name("connected").data(Map.of("connected", true)));
        } catch (IOException | IllegalStateException exception) {
            remove(user, emitter);
            emitter.completeWithError(exception);
        }
        return emitter;
    }

    public void send(UUID user, NotificationResponse value) {
        for (SseEmitter emitter : emitters.getOrDefault(user, new CopyOnWriteArrayList<>())) {
            try {
                emitter.send(SseEmitter.event().name("notification").id(value.id().toString()).data(value));
            } catch (IOException | IllegalStateException exception) {
                failures.increment();
                remove(user, emitter);
                emitter.completeWithError(exception);
            }
        }
    }

    @PreDestroy
    public void shutdown() {
        List<SseEmitter> active = new ArrayList<>();
        synchronized (this) {
            emitters.values().forEach(active::addAll);
            emitters.clear();
            count.set(0);
        }
        active.forEach(SseEmitter::complete);
    }

    private synchronized void remove(UUID user, SseEmitter emitter) {
        var list = emitters.get(user);
        if (list != null && list.remove(emitter)) {
            count.decrementAndGet();
            if (list.isEmpty()) emitters.remove(user, list);
        }
    }

    int connections(UUID user) {
        return emitters.getOrDefault(user, new CopyOnWriteArrayList<>()).size();
    }
}
