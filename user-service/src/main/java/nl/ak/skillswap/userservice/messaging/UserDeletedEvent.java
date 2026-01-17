package nl.ak.skillswap.userservice.messaging;

import java.time.OffsetDateTime;
import java.util.UUID;

public record UserDeletedEvent(
        UUID userId,
        String externalId,
        OffsetDateTime deletedAt
) {}

