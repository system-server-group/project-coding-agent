package com.system_server.ai_demo.commons.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 日付の値域チェック。{@link java.time.LocalDate} が 1900/01/01 以降 9999/12/31 以前であることを検証する。
 *
 * <p>
 * 未入力（{@code null}）は対象外（必須入力は別チェック）。日付として解釈可能かの形式チェックは日付入力部品
 * （{@code <input type="date">}）とバインダ側が担い、本チェックは値域のみを担保する。
 */
@Documented
@Constraint(validatedBy = DateInBoundsValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface DateInBounds {

    /**
     * エラーメッセージ。
     *
     * @return メッセージ
     */
    String message() default CheckMessages.INVALID_DATE;

    /**
     * バリデーショングループ。
     *
     * @return グループ
     */
    Class<?>[] groups() default {};

    /**
     * ペイロード。
     *
     * @return ペイロード
     */
    Class<? extends Payload>[] payload() default {};
}
