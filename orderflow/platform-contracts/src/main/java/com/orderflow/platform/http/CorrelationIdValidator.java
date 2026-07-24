package com.orderflow.platform.http;

import java.util.UUID;
import java.util.regex.Pattern;

public final class CorrelationIdValidator {

    public static final int MAX_LENGTH = 128;
    private static final Pattern SAFE_VALUE = Pattern.compile("[A-Za-z0-9._:-]+");

    private CorrelationIdValidator() {
        throw new AssertionError("Utility class must not be instantiated");
    }

    public static String validateOrGenerate(String incomingValue) {
        if (incomingValue != null) {
            String candidate = incomingValue.trim();
            if (!candidate.isEmpty()
                    && candidate.length() <= MAX_LENGTH
                    && SAFE_VALUE.matcher(candidate).matches()) {
                return candidate;
            }
        }
        return UUID.randomUUID().toString();
    }
}
