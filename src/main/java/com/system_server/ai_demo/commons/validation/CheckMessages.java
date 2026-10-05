package com.system_server.ai_demo.commons.validation;

/**
 * 入力値チェック システム共通仕様書のエラーメッセージ凡例を一元化した定数。
 *
 * <p>
 * 各所にメッセージをべた書きせず本定数を参照する。{@code {min}}・{@code {max}}・{@code {value}} は Bean Validation
 * が制約属性から補間する。{@code {boundLabel}} は注釈の属性値から補間する。文言は Java 定数（UTF-8 ソース）で保持し、 properties
 * のエンコーディング依存を避ける。
 */
public final class CheckMessages {

    /** 必須入力。 */
    public static final String REQUIRED_INPUT = "この項目は入力が必要です。";

    /** 必須選択（区分）。 */
    public static final String REQUIRED_SELECTION = "この項目は選択が必要です。";

    /** 最小文字数(N)。 */
    public static final String MIN_LENGTH = "{min}文字以上で入力してください。";

    /** 最大文字数(N)。 */
    public static final String MAX_LENGTH = "{max}文字以内で入力してください。";

    /** 使用文字。 */
    public static final String ALLOWED_CHARACTERS = "使用不可能な文字が含まれています。";

    /** 整数。 */
    public static final String INTEGER_VALUE = "整数を入力してください。";

    /** 最小数値(N)。 */
    public static final String MIN_VALUE = "{value}以上の数値を入力してください。";

    /** 最大数値(N)。 */
    public static final String MAX_VALUE = "{value}以下の数値を入力してください。";

    /** 日付形式。 */
    public static final String INVALID_DATE = "無効な日付です。";

    /** 日付下限(D)。 */
    public static final String DATE_LOWER_LIMIT = "{boundLabel}以降の日付を入力してください。";

    /** 日付上限(D)。 */
    public static final String DATE_UPPER_LIMIT = "{boundLabel}以前の日付を入力してください。";

    private CheckMessages() {}
}
