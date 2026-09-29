package com.system_server.ai_demo.commons.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 使用文字チェック。使用可能な文字（全角文字・半角英数字・半角記号）だけであることを検証する。
 *
 * <p>
 * 半角カタカナおよび制御文字は不可。未入力（{@code null}・空文字）は対象外（必須入力は別チェック）。{@link #allowNewline()} を {@code true}
 * にすると改行文字（CR・LF）を追加で許容する（案件概要・備考・メール本文など複数行入力向け）。
 */
@Documented
@Constraint(validatedBy = AllowedCharactersValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface AllowedCharacters {

    /**
     * 改行文字（CR・LF）を許容するか。複数行入力（案件概要・備考・メール本文）で {@code true} とする。
     *
     * @return 改行を許容する場合 {@code true}
     */
    boolean allowNewline() default false;

    /**
     * エラーメッセージ。
     *
     * @return メッセージ
     */
    String message() default CheckMessages.ALLOWED_CHARACTERS;

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
