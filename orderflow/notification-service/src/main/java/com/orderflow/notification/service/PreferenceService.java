package com.orderflow.notification.service;

import com.orderflow.notification.domain.NotificationPreference;
import com.orderflow.notification.dto.PreferenceRequest;
import com.orderflow.notification.dto.PreferenceResponse;
import com.orderflow.notification.error.NotificationDomainException;
import com.orderflow.notification.repository.NotificationPreferenceRepository;
import com.orderflow.platform.error.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PreferenceService {
    private final NotificationPreferenceRepository repository;
    private final DestinationMasker masker;
    private final Clock clock;

    public PreferenceService(NotificationPreferenceRepository repository, DestinationMasker masker, Clock clock) {
        this.repository = repository;
        this.masker = masker;
        this.clock = clock;
    }

    @Transactional
    public PreferenceResponse get(UUID user) {
        return map(repository.findById(user).orElseGet(() -> repository.save(
                NotificationPreference.defaults(user, Instant.now(clock)))));
    }

    @Transactional
    public PreferenceResponse update(UUID user, PreferenceRequest request) {
        NotificationPreference preference = repository.findById(user)
                .orElseGet(() -> NotificationPreference.defaults(user, Instant.now(clock)));
        try {
            preference.update(request.emailEnabled(), request.emailAddress(), request.smsEnabled(),
                    request.phoneNumber(), Instant.now(clock));
        } catch (IllegalArgumentException exception) {
            throw new NotificationDomainException(HttpStatus.BAD_REQUEST,
                    ErrorCode.NOTIFICATION_PREFERENCE_INVALID,
                    "Notification preference is invalid.");
        }
        return map(repository.save(preference));
    }

    private PreferenceResponse map(NotificationPreference preference) {
        return new PreferenceResponse(preference.isEmailEnabled(),
                masker.email(preference.getEmailAddress()), preference.isSmsEnabled(),
                masker.phone(preference.getPhoneNumber()), preference.getUpdatedAt(), preference.getVersion());
    }
}
