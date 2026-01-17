package nl.ak.skillswap.messageservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("MessageSanitizer")
class MessageSanitizerTest {

    private MessageSanitizer messageSanitizer;

    @BeforeEach
    void setUp() {
        messageSanitizer = new MessageSanitizer();
        ReflectionTestUtils.setField(messageSanitizer, "maxMessageLength", 2000);
        ReflectionTestUtils.setField(messageSanitizer, "minMessageLength", 1);
    }

    @Nested
    @DisplayName("sanitize")
    class Sanitize {

        @Test
        @DisplayName("should accept valid message")
        void shouldAcceptValidMessage() {
            String result = messageSanitizer.sanitize("Hello, world!");
            assertThat(result).isEqualTo("Hello, world!");
        }

        @Test
        @DisplayName("should throw for null message")
        void shouldThrowForNullMessage() {
            assertThatThrownBy(() -> messageSanitizer.sanitize(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("cannot be null");
        }

        @Test
        @DisplayName("should throw for empty message after trimming")
        void shouldThrowForEmptyMessageAfterTrimming() {
            assertThatThrownBy(() -> messageSanitizer.sanitize("   "))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("too short");
        }

        @Test
        @DisplayName("should throw for message exceeding max length")
        void shouldThrowForMessageExceedingMaxLength() {
            String longMessage = "a".repeat(2001);
            assertThatThrownBy(() -> messageSanitizer.sanitize(longMessage))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("exceeds maximum length");
        }

        @Test
        @DisplayName("should accept message at max length")
        void shouldAcceptMessageAtMaxLength() {
            String message = "a".repeat(2000);
            String result = messageSanitizer.sanitize(message);
            assertThat(result).hasSize(2000);
        }

        @Test
        @DisplayName("should trim whitespace")
        void shouldTrimWhitespace() {
            String result = messageSanitizer.sanitize("  hello world  ");
            assertThat(result).isEqualTo("hello world");
        }

        @Test
        @DisplayName("should remove null bytes and control characters")
        void shouldRemoveControlCharacters() {
            String result = messageSanitizer.sanitize("hello\u0000world\u0007test");
            assertThat(result).isEqualTo("helloworldtest");
        }

        @Test
        @DisplayName("should preserve newlines and tabs")
        void shouldPreserveNewlinesAndTabs() {
            String result = messageSanitizer.sanitize("hello\nworld\ttab");
            assertThat(result).contains("\n");
            assertThat(result).contains("\t");
        }

        @Test
        @DisplayName("should normalize Windows line endings to Unix")
        void shouldNormalizeWindowsLineEndings() {
            String result = messageSanitizer.sanitize("line1\r\nline2\rline3");
            assertThat(result).isEqualTo("line1\nline2\nline3");
        }

        @Test
        @DisplayName("should limit consecutive newlines to 3")
        void shouldLimitConsecutiveNewlines() {
            String result = messageSanitizer.sanitize("hello\n\n\n\n\n\nworld");
            assertThat(result).isEqualTo("hello\n\n\nworld");
        }

        @Test
        @DisplayName("should HTML encode script tags (XSS prevention)")
        void shouldHtmlEncodeScriptTags() {
            String result = messageSanitizer.sanitize("<script>alert('xss')</script>");
            assertThat(result).doesNotContain("<script>");
            assertThat(result).contains("&lt;script&gt;");
        }

        @Test
        @DisplayName("should HTML encode angle brackets")
        void shouldHtmlEncodeAngleBrackets() {
            String result = messageSanitizer.sanitize("a < b > c");
            assertThat(result).contains("&lt;");
            assertThat(result).contains("&gt;");
        }

        @Test
        @DisplayName("should HTML encode ampersand")
        void shouldHtmlEncodeAmpersand() {
            String result = messageSanitizer.sanitize("rock & roll");
            assertThat(result).contains("&amp;");
        }

        @Test
        @DisplayName("should HTML encode quotes")
        void shouldHtmlEncodeQuotes() {
            String result = messageSanitizer.sanitize("He said \"hello\"");
            assertThat(result).doesNotContain("\"");
        }
    }

    @Nested
    @DisplayName("sanitizeForWebSocket")
    class SanitizeForWebSocket {

        @Test
        @DisplayName("should return null for null input")
        void shouldReturnNullForNullInput() {
            String result = messageSanitizer.sanitizeForWebSocket(null);
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should escape JavaScript special characters")
        void shouldEscapeJavaScriptCharacters() {
            String result = messageSanitizer.sanitizeForWebSocket("test\nline");
            assertThat(result).doesNotContain("\n");
        }

        @Test
        @DisplayName("should escape single quotes for JavaScript")
        void shouldEscapeSingleQuotes() {
            String result = messageSanitizer.sanitizeForWebSocket("it's a test");
            assertThat(result).doesNotContain("'");
        }
    }

    @Nested
    @DisplayName("isContentSafe")
    class IsContentSafe {

        @Test
        @DisplayName("should return false for null content")
        void shouldReturnFalseForNullContent() {
            assertThat(messageSanitizer.isContentSafe(null)).isFalse();
        }

        @Test
        @DisplayName("should return true for safe content")
        void shouldReturnTrueForSafeContent() {
            assertThat(messageSanitizer.isContentSafe("Hello, how are you?")).isTrue();
            assertThat(messageSanitizer.isContentSafe("Let's meet at 5pm")).isTrue();
            assertThat(messageSanitizer.isContentSafe("Check out this link")).isTrue();
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "<script>alert(1)</script>",
                "<SCRIPT>alert(1)</SCRIPT>",
                "javascript:alert(1)",
                "JAVASCRIPT:void(0)",
                "<img onerror=alert(1)>",
                "<div onclick=alert(1)>",
                "<body onload=alert(1)>",
                "eval(document.cookie)",
                "document.cookie"
        })
        @DisplayName("should return false for XSS patterns")
        void shouldReturnFalseForXssPatterns(String content) {
            assertThat(messageSanitizer.isContentSafe(content)).isFalse();
        }

        @Test
        @DisplayName("should be case-insensitive for XSS detection")
        void shouldBeCaseInsensitiveForXssDetection() {
            assertThat(messageSanitizer.isContentSafe("<ScRiPt>")).isFalse();
            assertThat(messageSanitizer.isContentSafe("OnErRoR=")).isFalse();
            assertThat(messageSanitizer.isContentSafe("JavaScript:")).isFalse();
            assertThat(messageSanitizer.isContentSafe("EVAL(")).isFalse();
        }
    }
}
