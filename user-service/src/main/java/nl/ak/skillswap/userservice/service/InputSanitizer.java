package nl.ak.skillswap.userservice.service;

import lombok.extern.slf4j.Slf4j;
import org.owasp.encoder.Encode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Service for sanitizing user input to prevent XSS and injection attacks.
 * OWASP Top 10: A03 - Injection, A07 - Cross-Site Scripting (XSS)
 */
@Slf4j
@Service
public class InputSanitizer {

    @Value("${app.search.min-query-length:2}")
    private int minQueryLength;

    @Value("${app.search.max-query-length:100}")
    private int maxQueryLength;

    /**
     * Sanitize search query input.
     * - Trims whitespace
     * - Validates length
     * - Removes dangerous characters
     * - Encodes HTML entities
     *
     * @param query raw search query
     * @return sanitized query
     * @throws IllegalArgumentException if query is invalid
     */
    public String sanitizeSearchQuery(String query) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("Search query cannot be empty");
        }

        String trimmed = query.trim();

        if (trimmed.length() < minQueryLength) {
            throw new IllegalArgumentException("Search query must be at least " + minQueryLength + " characters");
        }

        if (trimmed.length() > maxQueryLength) {
            throw new IllegalArgumentException("Search query must not exceed " + maxQueryLength + " characters");
        }

        // Remove SQL wildcards that could be used for SQL injection patterns
        // Note: Our JPQL queries use parameterized queries, but we sanitize anyway
        String cleaned = trimmed
                .replace("\\", "")  // Remove backslashes
                .replace("'", "")   // Remove single quotes
                .replace("\"", "")  // Remove double quotes
                .replace(";", "")   // Remove semicolons
                .replace("--", "")  // Remove SQL comments
                .replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]", ""); // Remove control chars

        // Encode HTML entities
        return Encode.forHtml(cleaned);
    }

    /**
     * Sanitize general text input (display name, bio, etc.)
     */
    public String sanitizeText(String text) {
        if (text == null) {
            return null;
        }

        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return trimmed;
        }

        // Remove control characters except newlines and tabs
        String cleaned = trimmed.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]", "");

        // Normalize newlines
        cleaned = cleaned.replaceAll("\\r\\n", "\n").replaceAll("\\r", "\n");

        // Limit consecutive newlines
        cleaned = cleaned.replaceAll("\\n{4,}", "\n\n\n");

        // Encode HTML entities
        return Encode.forHtml(cleaned);
    }

    /**
     * Sanitize skill name input.
     */
    public String sanitizeSkillName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Skill name cannot be empty");
        }

        String trimmed = name.trim();
        if (trimmed.length() > 100) {
            throw new IllegalArgumentException("Skill name must not exceed 100 characters");
        }

        return Encode.forHtml(trimmed);
    }

    /**
     * Check if input contains potential injection patterns.
     * Returns true if safe, false if suspicious.
     */
    public boolean isSafeInput(String input) {
        if (input == null) {
            return true;
        }

        String lower = input.toLowerCase();
        return !lower.contains("<script") &&
                !lower.contains("javascript:") &&
                !lower.contains("onerror=") &&
                !lower.contains("onclick=") &&
                !lower.contains("onload=") &&
                !lower.contains("eval(") &&
                !lower.contains("union select") &&
                !lower.contains("drop table") &&
                !lower.contains("insert into") &&
                !lower.contains("delete from");
    }
}
