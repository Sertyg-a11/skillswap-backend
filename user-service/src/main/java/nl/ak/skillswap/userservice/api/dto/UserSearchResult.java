package nl.ak.skillswap.userservice.api.dto;

import nl.ak.skillswap.userservice.domain.Skill;
import nl.ak.skillswap.userservice.domain.User;

import java.util.List;
import java.util.UUID;

/**
 * DTO representing a user in search results.
 * Contains limited profile information for privacy.
 * Uses externalId (Keycloak sub) as the id for messaging consistency.
 */
public record UserSearchResult(
        UUID id,
        String displayName,
        String bio,
        List<SkillSummary> skills,
        double relevanceScore
) {
    public record SkillSummary(
            String name,
            String level,
            String category
    ) {
        public static SkillSummary from(Skill skill) {
            return new SkillSummary(skill.getName(), skill.getLevel(), skill.getCategory());
        }
    }

    public static UserSearchResult from(User user, List<Skill> skills, double relevanceScore) {
        return new UserSearchResult(
                UUID.fromString(user.getExternalId()),  // Use externalId (Keycloak sub) for messaging consistency
                user.getDisplayName(),
                user.getBio(),
                skills.stream().map(SkillSummary::from).toList(),
                relevanceScore
        );
    }
}
