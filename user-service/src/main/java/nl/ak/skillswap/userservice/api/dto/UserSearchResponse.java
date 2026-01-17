package nl.ak.skillswap.userservice.api.dto;

import java.util.List;

/**
 * Response DTO for user search.
 */
public record UserSearchResponse(
        List<UserSearchResult> results,
        int totalResults,
        String query,
        UserSearchRequest.SearchType searchType
) {
    public static UserSearchResponse of(List<UserSearchResult> results, String query, UserSearchRequest.SearchType type) {
        return new UserSearchResponse(results, results.size(), query, type);
    }
}
