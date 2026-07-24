package com.orderflow.auth.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.stereotype.Component;

@Component
public class SecurityAuditService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SecurityAuditService.class);

    public void record(SecurityAuditEvent audit) {
        LoggingEventBuilder event = LOGGER.atInfo()
                .addKeyValue("event", audit.event())
                .addKeyValue("eventTimestamp", audit.timestamp())
                .addKeyValue("userId", audit.userId())
                .addKeyValue("sessionId", audit.sessionId())
                .addKeyValue("provider", sanitize(audit.provider()))
                .addKeyValue("ipHash", sanitizeHash(audit.ipHash()))
                .addKeyValue("userAgentHash", sanitizeHash(audit.userAgentHash()))
                .addKeyValue("reasonCode", sanitize(audit.reasonCode()));
        if (MDC.get("correlationId") == null) {
            event.addKeyValue("correlationId", sanitize(audit.correlationId()));
        }
        event.log("Security audit event");
    }

    String sanitize(String value) {
        if (value == null) {
            return null;
        }
        return value.replaceAll("[\\r\\n\\t]", "_").substring(0, Math.min(value.length(), 128));
    }

    String sanitizeHash(String value) {
        if (value == null || !value.matches("[a-f0-9]{64}")) {
            return null;
        }
        return value;
    }
}
