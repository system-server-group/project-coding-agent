package com.system_server.ai_demo.commons.validation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;

class AllowedCharactersValidatorTest {

    private final AllowedCharactersValidator validator = new AllowedCharactersValidator();

    @Test
    void allows_halfWidthAlphanumericAndSymbols() {
        assertTrue(validator.isValid("Abc123", null));
        assertTrue(validator.isValid("a-b_c.d@example", null));
    }

    @Test
    void allows_fullWidthCharacters() {
        assertTrue(validator.isValid("案件あいうアイウ漢字", null));
    }

    @Test
    void allows_halfWidthSpace() {
        // 半角スペース（U+0020）は印字可能ASCIIの半角記号として許容する
        assertTrue(validator.isValid("a b", null));
        assertTrue(validator.isValid(" ", null));
    }

    @Test
    void allows_nullOrEmpty() {
        assertTrue(validator.isValid(null, null));
        assertTrue(validator.isValid("", null));
    }

    @Test
    void allows_newline_whenConfigured() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Validator jsr = factory.getValidator();
            // allowNewline=true では改行（LF・CR+LF）を許容する
            assertTrue(jsr.validate(new NewlineAllowed("line1\nline2")).isEmpty());
            assertTrue(jsr.validate(new NewlineAllowed("line1\r\nline2")).isEmpty());
            // 改行以外の制御文字（タブ）は依然として非許容
            assertFalse(jsr.validate(new NewlineAllowed("a\tb")).isEmpty());
        }
    }

    /** {@code allowNewline=true} 分岐を検証するための DTO。 */
    static class NewlineAllowed {

        @AllowedCharacters(allowNewline = true)
        private final String text;

        NewlineAllowed(String text) {
            this.text = text;
        }

        public String getText() {
            return text;
        }
    }

    @Test
    void rejects_halfWidthKatakana() {
        assertFalse(validator.isValid("ｱｲｳ", null));
    }

    @Test
    void rejects_controlCharacters() {
        assertFalse(validator.isValid("a\tb", null));
        assertFalse(validator.isValid("a\nb", null));
    }
}
