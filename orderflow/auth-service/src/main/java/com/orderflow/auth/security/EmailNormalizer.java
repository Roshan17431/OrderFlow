package com.orderflow.auth.security;

import java.text.Normalizer;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class EmailNormalizer {

    public String normalize(String email) {
        if (email == null) {
            return null;
        }
        return Normalizer.normalize(email.strip(), Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT);
    }
}
