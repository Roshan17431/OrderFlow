package com.orderflow.cart.client;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import org.springframework.web.client.ResourceAccessException;

public final class ProductFailureClassifier {

    private ProductFailureClassifier() {}

    public static boolean isTemporary(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof TemporaryProductServiceException
                    || current instanceof ResourceAccessException
                    || current instanceof ConnectException
                    || current instanceof SocketTimeoutException
                    || current instanceof CallNotPermittedException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
