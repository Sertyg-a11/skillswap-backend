package nl.ak.skillswap.userservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("InputSanitizer")
class InputSanitizerTest {

    private InputSanitizer inputSanitizer;

    @BeforeEach
    void setUp() {
        inputSanitizer = new InputSanitizer();
        ReflectionTestUtils.setField(inputSanitizer, "minQueryLength", 2);
        ReflectionTestUtils.setField(inputSanitizer, "maxQueryLength", 100);
    }

    @Nested
    @DisplayName("sanitizeSearchQuery")
    class SanitizeSearchQuery {

        @Test
        @DisplayName("should accept valid query")
        void shouldAcceptValidQuery() {
            String result = inputSanitizer.sanitizeSearchQuery("java developer");
            assertThat(result).isEqualTo("java developer");
        }

        @Test
        @DisplayName("should trim whitespace")
        void shouldTrimWhitespace() {
            String result = inputSanitizer.sanitizeSearchQuery("  hello world  ");
            assertThat(result).isEqualTo("hello world");
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "\t", "\n"})
        @DisplayName("should reject null, empty or blank queries")
        void shouldRejectNullEmptyOrBlank(String input) {
            assertThatThrownBy(() -> inputSanitizer.sanitizeSearchQuery(input))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("cannot be empty");
        }

        @Test
        @DisplayName("should reject query shorter than minimum length")
        void shouldRejectTooShortQuery() {
            assertThatThrownBy(() -> inputSanitizer.sanitizeSearchQuery("a"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("at least 2 characters");
        }

        @Test
        @DisplayName("should reject query longer than maximum length")
        void shouldRejectTooLongQuery() {
            String longQuery = "a".repeat(101);
            assertThatThrownBy(() -> inputSanitizer.sanitizeSearchQuery(longQuery))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("not exceed 100 characters");
        }

        @Test
        @DisplayName("should accept query at minimum length")
        void shouldAcceptMinLengthQuery() {
            String result = inputSanitizer.sanitizeSearchQuery("ab");
            assertThat(result).isEqualTo("ab");
        }

        @Test
        @DisplayName("should accept query at maximum length")
        void shouldAcceptMaxLengthQuery() {
            String query = "a".repeat(100);
            String result = inputSanitizer.sanitizeSearchQuery(query);
            assertThat(result).hasSize(100);
        }

        @Test
        @DisplayName("should remove single quotes (SQL injection prevention)")
        void shouldRemoveSingleQuotes() {
            String result = inputSanitizer.sanitizeSearchQuery("O'Brien");
            assertThat(result).isEqualTo("OBrien");
        }

        @Test
        @DisplayName("should remove double quotes")
        void shouldRemoveDoubleQuotes() {
            String result = inputSanitizer.sanitizeSearchQuery("test\"injection");
            assertThat(result).isEqualTo("testinjection");
        }

        @Test
        @DisplayName("should remove semicolons (SQL injection prevention)")
        void shouldRemoveSemicolons() {
            String result = inputSanitizer.sanitizeSearchQuery("test;DROP TABLE");
            assertThat(result).isEqualTo("testDROP TABLE");
        }

        @Test
        @DisplayName("should remove SQL comment markers")
        void shouldRemoveSqlComments() {
            String result = inputSanitizer.sanitizeSearchQuery("test--comment");
            assertThat(result).isEqualTo("testcomment");
        }

        @Test
        @DisplayName("should remove backslashes")
        void shouldRemoveBackslashes() {
            String result = inputSanitizer.sanitizeSearchQuery("test\\path");
            assertThat(result).isEqualTo("testpath");
        }

        @Test
        @DisplayName("should remove control characters")
        void shouldRemoveControlCharacters() {
            String result = inputSanitizer.sanitizeSearchQuery("test\u0000null\u0007bell");
            assertThat(result).isEqualTo("testnullbell");
        }

        @Test
        @DisplayName("should HTML encode special characters")
        void shouldHtmlEncodeSpecialCharacters() {
            String result = inputSanitizer.sanitizeSearchQuery("<script>alert(1)</script>");
            assertThat(result).doesNotContain("<script>");
            assertThat(result).contains("&lt;script&gt;");
        }
    }

    @Nested
    @DisplayName("sanitizeText")
    class SanitizeText {

        @Test
        @DisplayName("should return null for null input")
        void shouldReturnNullForNullInput() {
            String result = inputSanitizer.sanitizeText(null);
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should return empty string for empty input")
        void shouldReturnEmptyForEmptyInput() {
            String result = inputSanitizer.sanitizeText("");
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should trim whitespace")
        void shouldTrimWhitespace() {
            String result = inputSanitizer.sanitizeText("  hello  ");
            assertThat(result).isEqualTo("hello");
        }

        @Test
        @DisplayName("should remove control characters but keep newlines and tabs")
        void shouldRemoveControlCharactersButKeepNewlinesAndTabs() {
            String result = inputSanitizer.sanitizeText("hello\nworld\ttab\u0000null");
            assertThat(result).contains("\n");
            assertThat(result).contains("\t");
            assertThat(result).doesNotContain("\u0000");
        }

        @Test
        @DisplayName("should normalize Windows line endings")
        void shouldNormalizeWindowsLineEndings() {
            String result = inputSanitizer.sanitizeText("line1\r\nline2\rline3");
            assertThat(result).isEqualTo("line1\nline2\nline3");
        }

        @Test
        @DisplayName("should limit consecutive newlines to 3")
        void shouldLimitConsecutiveNewlines() {
            String result = inputSanitizer.sanitizeText("hello\n\n\n\n\nworld");
            assertThat(result).isEqualTo("hello\n\n\nworld");
        }

        @Test
        @DisplayName("should HTML encode special characters")
        void shouldHtmlEncodeSpecialCharacters() {
            String result = inputSanitizer.sanitizeText("<b>bold</b>");
            assertThat(result).contains("&lt;b&gt;");
        }
    }

    @Nested
    @DisplayName("sanitizeSkillName")
    class SanitizeSkillName {

        @Test
        @DisplayName("should accept valid skill name")
        void shouldAcceptValidSkillName() {
            String result = inputSanitizer.sanitizeSkillName("Java Programming");
            assertThat(result).isEqualTo("Java Programming");
        }

        @Test
        @DisplayName("should trim whitespace")
        void shouldTrimWhitespace() {
            String result = inputSanitizer.sanitizeSkillName("  Python  ");
            assertThat(result).isEqualTo("Python");
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        @DisplayName("should reject null, empty or blank skill names")
        void shouldRejectNullEmptyOrBlank(String input) {
            assertThatThrownBy(() -> inputSanitizer.sanitizeSkillName(input))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("cannot be empty");
        }

        @Test
        @DisplayName("should reject skill name longer than 100 characters")
        void shouldRejectTooLongSkillName() {
            String longName = "a".repeat(101);
            assertThatThrownBy(() -> inputSanitizer.sanitizeSkillName(longName))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("not exceed 100 characters");
        }

        @Test
        @DisplayName("should accept skill name at 100 characters")
        void shouldAcceptMaxLengthSkillName() {
            String name = "a".repeat(100);
            String result = inputSanitizer.sanitizeSkillName(name);
            assertThat(result).hasSize(100);
        }

        @Test
        @DisplayName("should HTML encode special characters")
        void shouldHtmlEncodeSpecialCharacters() {
            String result = inputSanitizer.sanitizeSkillName("C++ & Java");
            assertThat(result).contains("&amp;");
        }
    }

    @Nested
    @DisplayName("isSafeInput")
    class IsSafeInput {

        @Test
        @DisplayName("should return true for null input")
        void shouldReturnTrueForNullInput() {
            assertThat(inputSanitizer.isSafeInput(null)).isTrue();
        }

        @Test
        @DisplayName("should return true for safe input")
        void shouldReturnTrueForSafeInput() {
            assertThat(inputSanitizer.isSafeInput("Hello world")).isTrue();
            assertThat(inputSanitizer.isSafeInput("Java programming")).isTrue();
            assertThat(inputSanitizer.isSafeInput("user@email.com")).isTrue();
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "<script>alert(1)</script>",
                "<SCRIPT>alert(1)</SCRIPT>",
                "javascript:alert(1)",
                "JAVASCRIPT:alert(1)",
                "<img onerror=alert(1)>",
                "<div onclick=alert(1)>",
                "<body onload=alert(1)>",
                "eval(document.cookie)",
                "union select * from users",
                "DROP TABLE users",
                "INSERT INTO users",
                "DELETE FROM users"
        })
        @DisplayName("should return false for dangerous patterns")
        void shouldReturnFalseForDangerousPatterns(String input) {
            assertThat(inputSanitizer.isSafeInput(input)).isFalse();
        }

        @Test
        @DisplayName("should be case-insensitive for XSS detection")
        void shouldBeCaseInsensitiveForXssDetection() {
            assertThat(inputSanitizer.isSafeInput("<ScRiPt>")).isFalse();
            assertThat(inputSanitizer.isSafeInput("ONCLICK=")).isFalse();
            assertThat(inputSanitizer.isSafeInput("JavaScript:")).isFalse();
        }
    }
}
