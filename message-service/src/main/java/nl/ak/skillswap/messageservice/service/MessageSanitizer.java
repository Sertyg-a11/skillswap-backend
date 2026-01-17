package nl.ak.skillswap.messageservice.service;

import lombok.extern.slf4j.Slf4j;
import org.owasp.encoder.Encode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Service for sanitizing message content to prevent XSS attacks.
 * OWASP Top 10: A7 - Cross-Site Scripting (XSS)
 */
@Slf4j
@Service
public class MessageSanitizer {

    @Value("${app.messages.max-length:2000}")
    private int maxMessageLength;

    @Value("${app.messages.min-length:1}")
    private int minMessageLength;

    /**
     * Sanitize message content for safe storage and display.
     * - Trims whitespace
     * - Validates length
     * - Encodes HTML entities to prevent XSS
     *
     * @param rawContent the raw message content
     * @return sanitized content safe for storage and display
     * @throws IllegalArgumentException if content is invalid
     */
    public String sanitize(String rawContent) {
        if (rawContent == null) {
            throw new IllegalArgumentException("Message content cannot be null");
        }

        // Trim whitespace
        String trimmed = rawContent.trim();

        // Validate length
        if (trimmed.length() < minMessageLength) {
            throw new IllegalArgumentException("Message is too short");
        }

        if (trimmed.length() > maxMessageLength) {
            throw new IllegalArgumentException("Message exceeds maximum length of " + maxMessageLength + " characters");
        }

        // Remove null bytes and other control characters (except newlines and tabs)
        String cleaned = trimmed.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]", "");

        // Normalize newlines
        cleaned = cleaned.replaceAll("\\r\\n", "\n").replaceAll("\\r", "\n");

        // Limit consecutive newlines to prevent visual spam
        cleaned = cleaned.replaceAll("\\n{4,}", "\n\n\n");

        // HTML encode to prevent XSS when content is rendered
        String encoded = Encode.forHtml(cleaned);

        log.debug("Sanitized message: original length={}, sanitized length={}",
                rawContent.length(), encoded.length());

        return encoded;
    }

    /**
     * Sanitize content for WebSocket transmission.
     * Uses JavaScript encoding for safe client-side handling.
     *
     * @param content the content to sanitize
     * @return content safe for JavaScript/JSON context
     */
    public String sanitizeForWebSocket(String content) {
        if (content == null) {
            return null;
        }
        return Encode.forJavaScript(content);
    }

    /**
     * Validate that content doesn't contain suspicious patterns.
     * Returns true if content appears safe, false if suspicious.
     */
    public boolean isContentSafe(String content) {
        if (content == null) {
            return false;
        }

        // Check for common XSS patterns
        String lowerContent = content.toLowerCase();

        return !lowerContent.contains("<script") &&
                !lowerContent.contains("javascript:") &&
                !lowerContent.contains("onerror=") &&
                !lowerContent.contains("onclick=") &&
                !lowerContent.contains("onload=") &&
                !lowerContent.contains("eval(") &&
                !lowerContent.contains("document.cookie");
    }
}
