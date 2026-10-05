package com.system_server.ai_demo.commons.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.time.LocalDate;

/**
 * {@link DateInBounds} の検証本体。値域（1900/01/01〜9999/12/31）のみを判定する。
 *
 * <p>
 * 未入力（{@code null}）は対象外（必須入力は別チェック）。
 */
public class DateInBoundsValidator implements ConstraintValidator<DateInBounds, LocalDate> {

    private static final LocalDate MIN_DATE = LocalDate.of(1900, 1, 1);
    private static final LocalDate MAX_DATE = LocalDate.of(9999, 12, 31);

    @Override
    public boolean isValid(LocalDate value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        return !value.isBefore(MIN_DATE) && !value.isAfter(MAX_DATE);
    }
}
