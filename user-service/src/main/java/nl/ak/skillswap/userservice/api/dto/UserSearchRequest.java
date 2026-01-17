package nl.ak.skillswap.userservice.api.dto;

import jakarta.validation.constraints.Size;

/**
 * Request DTO for user search.
 * Supports searching by username, skill name, or skill category.
 */
public record UserSearchRequest(
        @Size(max = 100, message = "Query must not exceed 100 characters")
        String query,

        @Size(max = 64, message = "Skill category must not exceed 64 characters")
        String skillCategory,

        SearchType type
) {
    public enum SearchType {
        USERNAME,
        SKILL,
        CATEGORY,
        ALL  // Search across all fields
    }

    public SearchType type() {
        return type != null ? type : SearchType.ALL;
    }
}
