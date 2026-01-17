package nl.ak.skillswap.userservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.ak.skillswap.userservice.api.dto.UserSearchRequest;
import nl.ak.skillswap.userservice.api.dto.UserSearchResponse;
import nl.ak.skillswap.userservice.api.dto.UserSearchResult;
import nl.ak.skillswap.userservice.domain.Skill;
import nl.ak.skillswap.userservice.domain.User;
import nl.ak.skillswap.userservice.repository.SkillRepository;
import nl.ak.skillswap.userservice.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for searching users by username or skills.
 * Implements caching for performance optimization.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserSearchService {

    private final UserRepository userRepository;
    private final SkillRepository skillRepository;
    private final InputSanitizer inputSanitizer;
    private final RateLimitingService rateLimitingService;

    @Value("${app.search.max-results:20}")
    private int maxResults;

    /**
     * Search for users based on the search request.
     * Supports searching by username, skill name, skill category, or all combined.
     */
    @Transactional(readOnly = true)
    public UserSearchResponse search(UUID currentUserId, UserSearchRequest request) {
        // Rate limiting
        rateLimitingService.checkSearchRateLimit(currentUserId);

        // Sanitize input
        String sanitizedQuery = null;
        if (request.query() != null && !request.query().isBlank()) {
            sanitizedQuery = inputSanitizer.sanitizeSearchQuery(request.query());
        }

        String sanitizedCategory = null;
        if (request.skillCategory() != null && !request.skillCategory().isBlank()) {
            sanitizedCategory = inputSanitizer.sanitizeSearchQuery(request.skillCategory());
        }

        if (sanitizedQuery == null && sanitizedCategory == null) {
            throw new IllegalArgumentException("Either query or skillCategory must be provided");
        }

        Pageable pageable = PageRequest.of(0, maxResults);
        List<UserSearchResult> results;

        switch (request.type()) {
            case USERNAME -> results = searchByUsername(currentUserId, sanitizedQuery, pageable);
            case SKILL -> results = searchBySkill(currentUserId, sanitizedQuery, pageable);
            case CATEGORY -> results = searchByCategory(currentUserId, sanitizedCategory, pageable);
            default -> results = searchAll(currentUserId, sanitizedQuery, pageable);
        }

        log.debug("Search completed: query='{}', type={}, results={}",
                sanitizedQuery, request.type(), results.size());

        return UserSearchResponse.of(results, sanitizedQuery != null ? sanitizedQuery : sanitizedCategory, request.type());
    }

    /**
     * Search users by display name only.
     */
    private List<UserSearchResult> searchByUsername(UUID currentUserId, String query, Pageable pageable) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        List<User> users = userRepository.searchByDisplayName(query, currentUserId, pageable);
        return buildSearchResults(users, query);
    }

    /**
     * Search users by skill name.
     */
    private List<UserSearchResult> searchBySkill(UUID currentUserId, String query, Pageable pageable) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        // Find user IDs with matching skills
        List<UUID> userIds = skillRepository.findUserIdsBySkillNameContaining(query, currentUserId, pageable);

        if (userIds.isEmpty()) {
            return List.of();
        }

        // Fetch users (only active, matchable users)
        List<User> users = userRepository.findActiveMatchableByIds(userIds);

        return buildSearchResults(users, query);
    }

    /**
     * Search users by skill category.
     */
    private List<UserSearchResult> searchByCategory(UUID currentUserId, String category, Pageable pageable) {
        if (category == null || category.isBlank()) {
            return List.of();
        }

        // Find user IDs with skills in category
        List<UUID> userIds = skillRepository.findUserIdsBySkillCategory(category, currentUserId, pageable);

        if (userIds.isEmpty()) {
            return List.of();
        }

        // Fetch users
        List<User> users = userRepository.findActiveMatchableByIds(userIds);

        return buildSearchResults(users, category);
    }

    /**
     * Search across all fields (username and skills).
     */
    private List<UserSearchResult> searchAll(UUID currentUserId, String query, Pageable pageable) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        Set<UUID> foundUserIds = new LinkedHashSet<>();
        Map<UUID, Double> relevanceScores = new HashMap<>();

        // Search by username - higher relevance
        List<User> usernameMatches = userRepository.searchByDisplayName(query, currentUserId, pageable);
        for (User user : usernameMatches) {
            foundUserIds.add(user.getId());
            relevanceScores.put(user.getId(), 1.0); // High relevance for name match
        }

        // Search by skill - medium relevance
        List<UUID> skillUserIds = skillRepository.findUserIdsBySkillNameContaining(query, currentUserId, pageable);
        for (UUID userId : skillUserIds) {
            if (!foundUserIds.contains(userId)) {
                foundUserIds.add(userId);
                relevanceScores.put(userId, 0.7); // Medium relevance for skill match
            } else {
                // Boost score if found in both
                relevanceScores.merge(userId, 0.3, Double::sum);
            }
        }

        if (foundUserIds.isEmpty()) {
            return List.of();
        }

        // Limit results
        List<UUID> limitedUserIds = foundUserIds.stream()
                .limit(maxResults)
                .toList();

        // Fetch all users at once
        List<User> users = userRepository.findActiveMatchableByIds(limitedUserIds);

        // Build results with relevance scores
        return buildSearchResultsWithScores(users, relevanceScores);
    }

    /**
     * Build search results for a list of users.
     */
    private List<UserSearchResult> buildSearchResults(List<User> users, String query) {
        if (users.isEmpty()) {
            return List.of();
        }

        // Batch fetch all skills
        List<UUID> userIds = users.stream().map(User::getId).toList();
        Map<UUID, List<Skill>> skillsByUser = skillRepository.findByUserIdIn(userIds)
                .stream()
                .collect(Collectors.groupingBy(Skill::getUserId));

        return users.stream()
                .map(user -> UserSearchResult.from(
                        user,
                        skillsByUser.getOrDefault(user.getId(), List.of()),
                        calculateRelevance(user, skillsByUser.get(user.getId()), query)
                ))
                .sorted(Comparator.comparingDouble(UserSearchResult::relevanceScore).reversed())
                .toList();
    }

    /**
     * Build search results with pre-calculated relevance scores.
     */
    private List<UserSearchResult> buildSearchResultsWithScores(List<User> users, Map<UUID, Double> scores) {
        if (users.isEmpty()) {
            return List.of();
        }

        // Batch fetch all skills
        List<UUID> userIds = users.stream().map(User::getId).toList();
        Map<UUID, List<Skill>> skillsByUser = skillRepository.findByUserIdIn(userIds)
                .stream()
                .collect(Collectors.groupingBy(Skill::getUserId));

        return users.stream()
                .map(user -> UserSearchResult.from(
                        user,
                        skillsByUser.getOrDefault(user.getId(), List.of()),
                        scores.getOrDefault(user.getId(), 0.5)
                ))
                .sorted(Comparator.comparingDouble(UserSearchResult::relevanceScore).reversed())
                .toList();
    }

    /**
     * Calculate relevance score based on match quality.
     */
    private double calculateRelevance(User user, List<Skill> skills, String query) {
        double score = 0.5; // Base score

        String lowerQuery = query.toLowerCase();

        // Exact name match
        if (user.getDisplayName().equalsIgnoreCase(query)) {
            score = 1.0;
        }
        // Name starts with query
        else if (user.getDisplayName().toLowerCase().startsWith(lowerQuery)) {
            score = 0.9;
        }
        // Name contains query
        else if (user.getDisplayName().toLowerCase().contains(lowerQuery)) {
            score = 0.7;
        }

        // Boost for skill matches
        if (skills != null) {
            for (Skill skill : skills) {
                if (skill.getName().toLowerCase().contains(lowerQuery)) {
                    score = Math.min(1.0, score + 0.1);
                }
            }
        }

        return score;
    }
}
