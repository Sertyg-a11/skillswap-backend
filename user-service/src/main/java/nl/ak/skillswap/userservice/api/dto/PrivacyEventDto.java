package nl.ak.skillswap.userservice.api.dto;

import nl.ak.skillswap.userservice.domain.PrivacyEvent;
import nl.ak.skillswap.userservice.domain.PrivacyEventType;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PrivacyEventDto(
        Long id,
        UUID userId,
        PrivacyEventType type,
        String details,
        OffsetDateTime createdAt
) {
    public static PrivacyEventDto from(PrivacyEvent e) {
        return new PrivacyEventDto(
                e.getId(),
                e.getUserId(),
                e.getType(),
                e.getDetails(),
                e.getCreatedAt()
        );
    }
}
