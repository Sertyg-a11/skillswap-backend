package nl.ak.skillswap.userservice.api.dto;

import nl.ak.skillswap.userservice.domain.Skill;

import java.time.OffsetDateTime;
import java.util.UUID;

public record SkillDto(
        UUID id,
        String name,
        String level,
        String category,
        String description,
        OffsetDateTime createdAt
) {
    public static SkillDto from(Skill s) {
        return new SkillDto(
                s.getId(),
                s.getName(),
                s.getLevel(),
                s.getCategory(),
                s.getDescription(),
                s.getCreatedAt()
        );
    }
}
