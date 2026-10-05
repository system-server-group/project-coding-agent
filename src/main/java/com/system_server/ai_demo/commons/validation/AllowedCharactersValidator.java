package com.system_server.ai_demo.commons.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * {@link AllowedCharacters} の検証本体。
 *
 * <p>
 * 許容: 半角英数字・半角記号（印字可能 ASCII: U+0020〜U+007E）、および全角文字（U+00A0 以上）。 非許容: 制御文字（U+0000〜U+001F,
 * U+007F〜U+009F）、半角カタカナ（U+FF61〜U+FF9F）。
 */
public class AllowedCharactersValidator implements ConstraintValidator<AllowedCharacters, String> {

    private boolean allowNewline;

    @Override
    public void initialize(AllowedCharacters constraintAnnotation) {
        this.allowNewline = constraintAnnotation.allowNewline();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isEmpty()) {
            return true;
        }
        return value.codePoints().allMatch(this::isAllowed);
    }

    private boolean isAllowed(int codePoint) {
        if (allowNewline && (codePoint == 0x0A || codePoint == 0x0D)) {
            return true;
        }
        if (codePoint >= 0x20 && codePoint <= 0x7E) {
            return true;
        }
        if (codePoint >= 0xFF61 && codePoint <= 0xFF9F) {
            return false;
        }
        if (codePoint <= 0x9F) {
            return false;
        }
        return true;
    }
}
