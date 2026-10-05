package com.system_server.ai_demo.commons.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 日付の項目間（相関）チェック。{@link #from()}（自）と {@link #to()}（至）の日付が「自 ≤ 至（同日を含む）」であることを 検証する。
 *
 * <p>
 * クラス（型）レベルに宣言し、比較対象のプロパティ名・ラベルを属性で外出しすることで汎用化する。{@link #bound()} により、自を
 * 下限として扱う（{@link DateBound#LOWER}。至の項目にエラー）か、至を上限として扱う（{@link DateBound#UPPER}。自の項目に
 * エラー）かを切り替える。いずれのモードでも不変条件は同一で、変わるのはエラー付与先とメッセージの向きのみ。いずれかが未入力 （{@code null}）または {@code LocalDate}
 * でない場合は対象外（値域は {@link DateInBounds} 側で判定）。同一クラスに複数宣言でき、 下限・上限を同時に課すこともできる。
 */
@Documented
@Constraint(validatedBy = DateRangeValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(DateRange.List.class)
public @interface DateRange {

    /**
     * 自（範囲の起点。下限側）のプロパティ名。
     *
     * @return プロパティ名
     */
    String from();

    /**
     * 至（範囲の終点。上限側）のプロパティ名。
     *
     * @return プロパティ名
     */
    String to();

    /**
     * 自を下限として扱う（至にエラー）か、至を上限として扱う（自にエラー）か。既定は下限（{@link DateBound#LOWER}）。
     *
     * @return 境界の扱い
     */
    DateBound bound() default DateBound.LOWER;

    /**
     * 境界側の項目（下限モードは自、上限モードは至）のラベル。エラーメッセージの {@code {boundLabel}} に補間される。
     *
     * @return ラベル
     */
    String boundLabel();

    /**
     * エラーメッセージ。空の場合は {@link #bound()} に応じて既定の文言（下限＝{@link CheckMessages#DATE_LOWER_LIMIT}／
     * 上限＝{@link CheckMessages#DATE_UPPER_LIMIT}）を使用する。
     *
     * @return メッセージ
     */
    String message() default "";

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

    /**
     * 同一クラスに複数の {@link DateRange} を宣言するためのコンテナ。
     */
    @Documented
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @interface List {

        /**
         * 宣言された {@link DateRange} の配列。
         *
         * @return 制約の配列
         */
        DateRange[] value();
    }
}
